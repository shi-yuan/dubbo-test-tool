/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nlpcn.dubbotest.util;

import com.alibaba.dubbo.common.utils.ClassHelper;
import com.alibaba.dubbo.common.utils.CompatibleTypeUtils;
import com.alibaba.dubbo.common.utils.ReflectUtils;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * PojoUtils. Travel object deeply, and convert complex type to simple type.
 * <p/>
 * Simple type below will be remained:
 * <ul>
 * <li> Primitive Type, also include <b>String</b>, <b>Number</b>(Integer, Long), <b>Date</b>
 * <li> Array of Primitive Type
 * <li> Collection, eg: List, Map, Set etc.
 * </ul>
 * <p/>
 * Other type will be covert to a map which contains the attributes and value pair of object.
 */
public class PojoUtils {

    public static Object[] realize(Object[] objs, Class<?>[] types, Type[] gtypes) {
        if (objs.length != types.length || objs.length != gtypes.length)
            throw new IllegalArgumentException("args.length != types.length");
        Object[] dests = new Object[objs.length];
        for (int i = 0; i < objs.length; i++) {
            dests[i] = realize(objs[i], types[i], gtypes[i]);
        }
        return dests;
    }

    public static Object realize(Object pojo, Class<?> type, Type genericType) {
        return realize0(pojo, type, genericType, new IdentityHashMap<Object, Object>());
    }

    private static class PojoInvocationHandler implements InvocationHandler {

        private Map<Object, Object> map;

        public PojoInvocationHandler(Map<Object, Object> map) {
            this.map = map;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(map, args);
            }
            String methodName = method.getName();
            Object value = null;
            if (methodName.length() > 3 && methodName.startsWith("get")) {
                value = map.get(methodName.substring(3, 4).toLowerCase() + methodName.substring(4));
            } else if (methodName.length() > 2 && methodName.startsWith("is")) {
                value = map.get(methodName.substring(2, 3).toLowerCase() + methodName.substring(3));
            } else {
                value = map.get(methodName.substring(0, 1).toLowerCase() + methodName.substring(1));
            }
            if (value instanceof Map<?, ?> && !Map.class.isAssignableFrom(method.getReturnType())) {
                value = realize0((Map<String, Object>) value, method.getReturnType(), null,
                        new IdentityHashMap<Object, Object>());
            }
            return value;
        }
    }

    @SuppressWarnings("unchecked")
    private static Collection<Object> createCollection(Class<?> type, int len) {
        if (type.isAssignableFrom(ArrayList.class)) {
            return new ArrayList<Object>(len);
        }
        if (type.isAssignableFrom(HashSet.class)) {
            return new HashSet<Object>(len);
        }
        if (!type.isInterface() && !Modifier.isAbstract(type.getModifiers())) {
            try {
                return (Collection<Object>) type.newInstance();
            } catch (Exception e) {
                // ignore
            }
        }
        return new ArrayList<Object>();
    }

    private static Map createMap(Map src) {
        Class<? extends Map> cl = src.getClass();
        Map result = null;
        if (HashMap.class == cl) {
            result = new HashMap();
        } else if (Hashtable.class == cl) {
            result = new Hashtable();
        } else if (IdentityHashMap.class == cl) {
            result = new IdentityHashMap();
        } else if (LinkedHashMap.class == cl) {
            result = new LinkedHashMap();
        } else if (Properties.class == cl) {
            result = new Properties();
        } else if (TreeMap.class == cl) {
            result = new TreeMap();
        } else if (WeakHashMap.class == cl) {
            return new WeakHashMap();
        } else if (ConcurrentHashMap.class == cl) {
            result = new ConcurrentHashMap();
        } else if (ConcurrentSkipListMap.class == cl) {
            result = new ConcurrentSkipListMap();
        } else {
            try {
                result = cl.newInstance();
            } catch (Exception e) { /* ignore */ }

            if (result == null) {
                try {
                    Constructor<?> constructor = cl.getConstructor(Map.class);
                    result = (Map) constructor.newInstance(Collections.EMPTY_MAP);
                } catch (Exception e) { /* ignore */ }
            }
        }

        if (result == null) {
            result = new HashMap<>();
        }

        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object realize0(Object pojo, Class<?> type, Type genericType, final Map<Object, Object> history) {
        if (pojo == null) {
            return null;
        }

        if (type != null && type.isEnum() && pojo.getClass() == String.class) {
            return Enum.valueOf((Class<Enum>) type, (String) pojo);
        }

        if (ReflectUtils.isPrimitives(pojo.getClass()) && !(type != null && type.isArray() && type.getComponentType()
                .isEnum() && pojo.getClass() == String[].class)) {
            return CompatibleTypeUtils.compatibleTypeConvert(pojo, type);
        }

        Object o = history.get(pojo);

        if (o != null) {
            return o;
        }

        history.put(pojo, pojo);

        if (pojo.getClass().isArray()) {
            if (Collection.class.isAssignableFrom(type)) {
                Class<?> ctype = pojo.getClass().getComponentType();
                int len = Array.getLength(pojo);
                Collection dest = createCollection(type, len);
                history.put(pojo, dest);
                for (int i = 0; i < len; i++) {
                    Object obj = Array.get(pojo, i);
                    Object value = realize0(obj, ctype, null, history);
                    dest.add(value);
                }
                return dest;
            } else {
                Class<?> ctype = type.isArray() ? type.getComponentType() : pojo.getClass().getComponentType();
                int len = Array.getLength(pojo);
                Object dest = Array.newInstance(ctype, len);
                history.put(pojo, dest);
                for (int i = 0; i < len; i++) {
                    Object obj = Array.get(pojo, i);
                    Object value = realize0(obj, ctype, null, history);
                    Array.set(dest, i, value);
                }
                return dest;
            }
        }

        if (pojo instanceof Collection<?>) {
            if (type.isArray()) {
                Class<?> ctype = type.getComponentType();
                Collection<Object> src = (Collection<Object>) pojo;
                int len = src.size();
                Object dest = Array.newInstance(ctype, len);
                history.put(pojo, dest);
                int i = 0;
                for (Object obj : src) {
                    Object value = realize0(obj, ctype, null, history);
                    Array.set(dest, i, value);
                    i++;
                }
                return dest;
            } else {
                Collection<Object> src = (Collection<Object>) pojo;
                int len = src.size();
                Collection<Object> dest = createCollection(type, len);
                history.put(pojo, dest);
                for (Object obj : src) {
                    Type keyType = getGenericClassByIndex(genericType, 0);
                    Class<?> keyClazz = obj.getClass();
                    if (keyType instanceof Class) {
                        keyClazz = (Class<?>) keyType;
                    }
                    Object value = realize0(obj, keyClazz, keyType, history);
                    dest.add(value);
                }
                return dest;
            }
        }

        if (pojo instanceof Map<?, ?> && type != null) {
            Object className = ((Map<Object, Object>) pojo).get("class");
            if (className instanceof String) {
                try {
                    type = ClassHelper.forName((String) className);
                } catch (ClassNotFoundException e) {
                    // ignore
                }
            }

            // special logic for enum
            if (type.isEnum()) {
                Object name = ((Map<Object, Object>) pojo).get("name");
                if (name != null) {
                    return Enum.valueOf((Class<Enum>) type, name.toString());
                }
            }
            Map<Object, Object> map;
            // when return type is not the subclass of return type from the signature and not an interface
            if (!type.isInterface() && !type.isAssignableFrom(pojo.getClass())) {
                try {
                    map = (Map<Object, Object>) type.newInstance();
                    Map<Object, Object> mapPojo = (Map<Object, Object>) pojo;
                    map.putAll(mapPojo);
                    map.remove("class");
                } catch (Exception e) {
                    //ignore error
                    map = (Map<Object, Object>) pojo;
                }
            } else {
                map = (Map<Object, Object>) pojo;
            }

            if (Map.class.isAssignableFrom(type) || type == Object.class) {
                final Map<Object, Object> result = createMap(map);
                history.put(pojo, result);
                for (Map.Entry<Object, Object> entry : map.entrySet()) {
                    Type keyType = getGenericClassByIndex(genericType, 0);
                    Type valueType = getGenericClassByIndex(genericType, 1);
                    Class<?> keyClazz;
                    if (keyType instanceof Class) {
                        keyClazz = (Class<?>) keyType;
                    } else if (keyType instanceof ParameterizedType) {
                        keyClazz = (Class<?>) ((ParameterizedType) keyType).getRawType();
                    } else {
                        keyClazz = entry.getKey() == null ? null : entry.getKey().getClass();
                    }
                    Class<?> valueClazz;
                    if (valueType instanceof Class) {
                        valueClazz = (Class<?>) valueType;
                    } else if (valueType instanceof ParameterizedType) {
                        valueClazz = (Class<?>) ((ParameterizedType) valueType).getRawType();
                    } else {
                        valueClazz = entry.getValue() == null ? null : entry.getValue().getClass();
                    }

                    Object key =
                            keyClazz == null ? entry.getKey() : realize0(entry.getKey(), keyClazz, keyType, history);
                    Object value = valueClazz == null ?
                            entry.getValue() :
                            realize0(entry.getValue(), valueClazz, valueType, history);
                    result.put(key, value);
                }
                return result;
            } else if (type.isInterface()) {
                Object dest = Proxy
                        .newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class<?>[]{type},
                                new PojoInvocationHandler(map));
                history.put(pojo, dest);
                return dest;
            } else {
                Object dest = newInstance(type);
                history.put(pojo, dest);
                for (Map.Entry<Object, Object> entry : map.entrySet()) {
                    Object key = entry.getKey();
                    if (key instanceof String) {
                        String name = (String) key;
                        Object value = entry.getValue();
                        if (value != null) {
                            Method method = getSetterMethod(dest.getClass(), name, value.getClass());
                            Field field = getField(dest.getClass(), name);
                            if (method != null) {
                                if (!method.isAccessible())
                                    method.setAccessible(true);
                                Type ptype = method.getGenericParameterTypes()[0];
                                value = realize0(value, method.getParameterTypes()[0], ptype, history);
                                try {
                                    method.invoke(dest, value);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    throw new RuntimeException(
                                            "Failed to set pojo " + dest.getClass().getSimpleName() + " property " + name
                                                    + " value " + value + "(" + value.getClass() + "), cause: " + e
                                                    .getMessage(), e);
                                }
                            } else if (field != null) {
                                value = realize0(value, field.getType(), field.getGenericType(), history);
                                try {
                                    field.set(dest, value);
                                } catch (IllegalAccessException e) {
                                    throw new RuntimeException(
                                            "Failed to set filed " + name + " of pojo " + dest.getClass().getName() + " : "
                                                    + e.getMessage(), e);
                                }
                            }
                        }
                    }
                }
                if (dest instanceof Throwable) {
                    Object message = map.get("message");
                    if (message instanceof String) {
                        try {
                            Field filed = Throwable.class.getDeclaredField("detailMessage");
                            if (!filed.isAccessible()) {
                                filed.setAccessible(true);
                            }
                            filed.set(dest, message);
                        } catch (Exception e) {
                        }
                    }
                }
                return dest;
            }
        }
        return pojo;
    }

    /**
     * Get parameterized type
     *
     * @param genericType generic type
     * @param index       index of the target parameterized type
     * @return Return Person.class for List<Person>, return Person.class for Map<String, Person> when index=0
     */
    private static Type getGenericClassByIndex(Type genericType, int index) {
        Type clazz = null;
        // find parameterized type
        if (genericType instanceof ParameterizedType) {
            ParameterizedType t = (ParameterizedType) genericType;
            Type[] types = t.getActualTypeArguments();
            clazz = types[index];
        }
        return clazz;
    }

    private static Object newInstance(Class<?> cls) {
        try {
            return cls.newInstance();
        } catch (Throwable t) {
            try {
                Constructor<?>[] constructors = cls.getDeclaredConstructors();
                if (constructors != null && constructors.length == 0) {
                    throw new RuntimeException("Illegal constructor: " + cls.getName());
                }
                Constructor<?> constructor = constructors[0];
                if (constructor.getParameterTypes().length > 0) {
                    for (Constructor<?> c : constructors) {
                        if (c.getParameterTypes().length < constructor.getParameterTypes().length) {
                            constructor = c;
                            if (constructor.getParameterTypes().length == 0) {
                                break;
                            }
                        }
                    }
                }
                constructor.setAccessible(true);
                return constructor.newInstance(new Object[constructor.getParameterTypes().length]);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }

    private static Method getSetterMethod(Class<?> cls, String property, Class<?> valueCls) {
        String name = "set" + property.substring(0, 1).toUpperCase() + property.substring(1);
        Method method = null;
        try {
            method = cls.getMethod(name, valueCls);
        } catch (NoSuchMethodException e) {
            for (Method m : cls.getMethods()) {
                if (ReflectUtils.isBeanPropertyWriteMethod(m) && m.getName().equals(name)) {
                    method = m;
                }
            }
        }

        return method;
    }

    private static Field getField(Class<?> cls, String fieldName) {
        Field result = null;
        try {
            result = cls.getDeclaredField(fieldName);
            result.setAccessible(true);
        } catch (NoSuchFieldException e) {
            for (Field field : cls.getFields()) {
                if (fieldName.equals(field.getName()) && ReflectUtils.isPublicInstanceField(field)) {
                    result = field;
                    break;
                }
            }
        }

        return result;
    }

}
