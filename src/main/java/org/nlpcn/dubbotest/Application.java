package org.nlpcn.dubbotest;

import com.alibaba.dubbo.common.utils.ClassHelper;
import com.alibaba.dubbo.common.utils.ReflectUtils;
import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.ReferenceConfig;
import com.alibaba.dubbo.config.RegistryConfig;
import com.alibaba.dubbo.rpc.service.GenericService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.validation.Valid;

import org.nlpcn.dubbotest.util.ArtifactUtils;
import org.nlpcn.dubbotest.util.PojoUtils;
import org.nlpcn.dubbotest.vm.ApiVM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class Application {

    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    private static final String APPLICATION_NAME = "api-generic-consumer";

    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @PostMapping(value = "/dubbo/test", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object testDubboApi(@RequestBody @Valid ApiVM api, @RequestParam(value = "_force", defaultValue = "false") boolean force) throws Exception {
        if (!StringUtils.hasText(api.getName())) {
            api.setName(APPLICATION_NAME);
            LOG.info("use default application name: {}", APPLICATION_NAME);
        }
        if (!StringUtils.hasText(api.getService())) {
            String m = api.getMethod();
            int i = m.lastIndexOf(".");
            api.setService(m.substring(0, i));
            api.setMethod(m.substring(i + 1));
            LOG.info("[service] not found, substing method[{}]：service[{}]，method[{}]", m, api.getService(), api.getMethod());
        }

        //
        String[] arr = com.alibaba.dubbo.common.utils.StringUtils.split(api.getDependency(), ':');
        Path artifactPath = ArtifactUtils.download(force, "repository", arr[0], arr[1], arr[2]);

        //
        List<URL> urls = new LinkedList<>();
        Files.walkFileTree(artifactPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path p, BasicFileAttributes attrs) throws IOException {
                File f = p.toFile();
                if (f.getName().toLowerCase().endsWith(".jar")) {
                    urls.add(f.toURI().toURL());
                }

                return FileVisitResult.CONTINUE;
            }
        });

        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader cl = new URLClassLoader(urls.toArray(new URL[0]), contextClassLoader)) {
            Thread.currentThread().setContextClassLoader(cl);

            List<Object> args = Optional.ofNullable(api.getArgs()).orElse(Collections.emptyList());
            Method invokeMethod = findMethod(ClassHelper.forName(api.getService()), api.getMethod(), args);
            if (invokeMethod != null) {
                Object[] array = PojoUtils.realize(args.toArray(), invokeMethod.getParameterTypes(), invokeMethod.getGenericParameterTypes());

                //
                RegistryConfig registry = new RegistryConfig();
                if (StringUtils.hasText(api.getAddress())) {
                    registry.setAddress(api.getAddress());
                }
                ApplicationConfig application = new ApplicationConfig();
                application.setRegistry(registry);
                application.setName(api.getName());

                ReferenceConfig<GenericService> reference = new ReferenceConfig<>();
                reference.setApplication(application);
                reference.setInterface(api.getService());
                reference.setGeneric(true);
                if (StringUtils.hasText(api.getUrl())) {
                    reference.setUrl(api.getUrl());
                }
                if (api.getTimeout() != null) {
                    reference.setTimeout(api.getTimeout());
                }

                long start = System.currentTimeMillis();

                Object result = reference.get().$invoke(api.getMethod(), Arrays.stream(invokeMethod.getParameterTypes()).map(Class::getName).toArray(String[]::new), array);

                LOG.info("invoke method[{}.{}], took {}ms", api.getService(), api.getMethod(), System.currentTimeMillis() - start);

                return mapper.readValue(mapper.writeValueAsString(PojoUtils.realize(result, invokeMethod.getReturnType(), invokeMethod.getGenericReturnType())), Object.class);
            } else {
                LOG.warn("method[{}.{}] not found", api.getService(), api.getMethod());

                return "No such method[" + api.getMethod() + "] in service[" + api.getService() + "]";
            }
        } catch (Throwable t) {
            LOG.error("failed to invoke method[{}.{}]: ", api.getService(), api.getMethod(), t);

            return "Failed to invoke method[" + api.getService() + "." + api.getMethod() + "], cause: " + com.alibaba.dubbo.common.utils.StringUtils.toString(t);
        } finally {
            Thread.currentThread().setContextClassLoader(contextClassLoader);
        }
    }

    private Method findMethod(Class<?> iface, String method, List<Object> args) throws ClassNotFoundException {
        Method[] methods = iface.getMethods();
        for (Method m : methods) {
            if (m.getName().equals(method) && isMatch(m.getParameterTypes(), args)) {
                return m;
            }
        }

        return null;
    }

    private boolean isMatch(Class<?>[] types, List<Object> args) throws ClassNotFoundException {
        if (types.length != args.size()) {
            return false;
        }

        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i];
            Object arg = args.get(i);

            if (arg == null) {
                if (type.isPrimitive()) {
                    throw new NullPointerException(String.format("The type of No.%d parameter is primitive(%s), but the value passed is null.", i + 1, type.getName()));
                }

                continue;
            }

            if (ReflectUtils.isPrimitive(arg.getClass())) {
                if (!ReflectUtils.isPrimitive(type)) {
                    return false;
                }
            } else if (arg instanceof Map) {
                String name = (String) ((Map<?, ?>) arg).get("class");
                Class<?> cls = arg.getClass();
                if (name != null && name.length() > 0) {
                    cls = ClassHelper.forName(name);
                }

                if (!type.isAssignableFrom(cls)) {
                    return false;
                }
            } else if (arg instanceof Collection) {
                if (!type.isArray() && !type.isAssignableFrom(arg.getClass())) {
                    return false;
                }
            } else {
                if (!type.isAssignableFrom(arg.getClass())) {
                    return false;
                }
            }
        }

        return true;
    }

}
