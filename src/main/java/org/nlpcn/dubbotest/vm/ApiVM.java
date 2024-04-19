package org.nlpcn.dubbotest.vm;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.util.List;

public class ApiVM {

    /**
     * dependency
     */
    @NotBlank
    @Pattern(regexp = "(([^:]+):){2}([^:]+)")
    private String dependency;

    /**
     * application name
     */
    private String name;

    /**
     * register center address
     */
    private String address;

    /**
     * url for peer-to-peer invocation
     */
    private String url;

    /**
     * service name
     */
    private String service;

    /**
     * method name
     */
    @NotBlank
    private String method;

    /**
     * args
     */
    private List<Object> args;

    /**
     * timout
     */
    private Integer timeout;

    /**
     * version
     */
    private String version;

    /**
     * group
     */
    private String group;

    public String getDependency() {
        return dependency;
    }

    public void setDependency(String dependency) {
        this.dependency = dependency;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public List<Object> getArgs() {
        return args;
    }

    public void setArgs(List<Object> args) {
        this.args = args;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }
}
