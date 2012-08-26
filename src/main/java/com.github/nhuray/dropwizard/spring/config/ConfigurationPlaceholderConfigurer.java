package com.github.nhuray.dropwizard.spring.config;

import com.yammer.dropwizard.config.Configuration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionVisitor;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.util.Assert;
import org.springframework.util.PropertiesPersister;
import org.springframework.util.PropertyPlaceholderHelper;
import org.springframework.util.StringValueResolver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Properties;

import static org.codehaus.jackson.annotate.JsonAutoDetect.Visibility.NONE;
import static org.codehaus.jackson.annotate.JsonMethod.GETTER;

/**
 *
 */
public class ConfigurationPlaceholderConfigurer implements BeanFactoryPostProcessor {

    /** Logger available to subclasses */
    protected final Log logger = LogFactory.getLog(getClass());

    private Configuration configuration;
    private ObjectMapper mapper;

    // ~ Copied from {@link PlaceholderConfigurerSupport} ----------------------------------------------------------------------

    /** Default placeholder prefix: {@value} */
    public static final String DEFAULT_PLACEHOLDER_PREFIX = "${";

    /** Default placeholder suffix: {@value} */
    public static final String DEFAULT_PLACEHOLDER_SUFFIX = "}";

    /** Default value separator: {@value} */
    public static final String DEFAULT_VALUE_SEPARATOR = ":";

    /** Defaults to {@value #DEFAULT_PLACEHOLDER_PREFIX} */
    protected String placeholderPrefix = DEFAULT_PLACEHOLDER_PREFIX;

    /** Defaults to {@value #DEFAULT_PLACEHOLDER_SUFFIX} */
    protected String placeholderSuffix = DEFAULT_PLACEHOLDER_SUFFIX;

    /** Defaults to {@value #DEFAULT_VALUE_SEPARATOR} */
    protected String valueSeparator = DEFAULT_VALUE_SEPARATOR;

    protected boolean ignoreUnresolvablePlaceholders = false;

    protected String nullValue;

    private PropertiesPersister propertiesPersister ;


    public ConfigurationPlaceholderConfigurer(Configuration configuration) {
        this.configuration = configuration;

        // Initialize ObjectMapper
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(GETTER, NONE);     // Only Serialize fields

        this.propertiesPersister = new JsonPropertiesPersister(mapper);
        this.mapper = mapper;
    }


    /**
     * {@linkplain #processProperties process} properties against the given bean factory.
     *
     * @throws BeanInitializationException if any properties cannot be loaded
     */
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        try {
            Properties props = new Properties();

            // Load properties
            loadProperties(props);

            // Process properties
            processProperties(beanFactory, props);
        } catch (IOException ex) {
            throw new BeanInitializationException("Could not load properties from Dropwizard configuration", ex);
        }
    }

    /**
     * {@linkplain #loadProperties load} properties against the Configuration.
     *
     * @throws BeanInitializationException if any properties cannot be loaded
     */
    private void loadProperties(Properties props) throws BeansException, IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        mapper.writeValue(stream, configuration);
        propertiesPersister.load(props, new ByteArrayInputStream(stream.toByteArray()));
    }

    /**
     * @param beanFactory
     * @param props
     * @throws BeansException
     */
    protected void processProperties(ConfigurableListableBeanFactory beanFactory, Properties props) throws BeansException {
        StringValueResolver valueResolver = new PlaceholderResolvingStringValueResolver(props);
        doProcessProperties(beanFactory, valueResolver);
    }


    /**
     * Set the prefix that a placeholder string starts with.
     * The default is {@value #DEFAULT_PLACEHOLDER_PREFIX}.
     */
    public void setPlaceholderPrefix(String placeholderPrefix) {
        this.placeholderPrefix = placeholderPrefix;
    }

    /**
     * Set the suffix that a placeholder string ends with.
     * The default is {@value #DEFAULT_PLACEHOLDER_SUFFIX}.
     */
    public void setPlaceholderSuffix(String placeholderSuffix) {
        this.placeholderSuffix = placeholderSuffix;
    }

    /**
     * Specify the separating character between the placeholder variable
     * and the associated default value, or {@code null} if no such
     * special character should be processed as a value separator.
     * The default is {@value #DEFAULT_VALUE_SEPARATOR}.
     */
    public void setValueSeparator(String valueSeparator) {
        this.valueSeparator = valueSeparator;
    }

    /**
     * Set a value that should be treated as {@code null} when
     * resolved as a placeholder value: e.g. "" (empty String) or "null".
     * <p>Note that this will only apply to full property values,
     * not to parts of concatenated values.
     * <p>By default, no such null value is defined. This means that
     * there is no way to express {@code null} as a property
     * value unless you explicitly map a corresponding value here.
     */
    public void setNullValue(String nullValue) {
        this.nullValue = nullValue;
    }

    /**
     * Set whether to ignore unresolvable placeholders.
     * <p>Default is "false": An exception will be thrown if a placeholder fails
     * to resolve. Switch this flag to "true" in order to preserve the placeholder
     * String as-is in such a case, leaving it up to other placeholder configurers
     * to resolve it.
     */
    public void setIgnoreUnresolvablePlaceholders(boolean ignoreUnresolvablePlaceholders) {
        this.ignoreUnresolvablePlaceholders = ignoreUnresolvablePlaceholders;
    }

    public void setMapper(ObjectMapper mapper) {
        Assert.notNull(mapper, "mapper is required");
        this.mapper = mapper;
    }

    /**
     * Set the PropertiesPersister to use for parsing properties files.
     * The default is DefaultPropertiesPersister.
     *
     * @see org.springframework.util.DefaultPropertiesPersister
     */
    public void setPropertiesPersister(PropertiesPersister propertiesPersister) {
        Assert.notNull(propertiesPersister, "propertiesPersister is required");
        this.propertiesPersister = propertiesPersister;
    }

    // ~ Copied from {@link PropertyPlaceholderConfigurer} ----------------------------------------------------------------------


    private class PlaceholderResolvingStringValueResolver implements StringValueResolver {

        private final PropertyPlaceholderHelper helper;
        private final PropertyPlaceholderHelper.PlaceholderResolver resolver;

        public PlaceholderResolvingStringValueResolver(Properties props) {
            this.helper = new PropertyPlaceholderHelper(
                    placeholderPrefix, placeholderSuffix, valueSeparator, ignoreUnresolvablePlaceholders);
            this.resolver = new PropertyPlaceholderConfigurerResolver(props);
        }

        public String resolveStringValue(String strVal) throws BeansException {
            String value = this.helper.replacePlaceholders(strVal, this.resolver);
            return (value.equals(nullValue) ? null : value);
        }
    }

    private class PropertyPlaceholderConfigurerResolver implements PropertyPlaceholderHelper.PlaceholderResolver {
        private final Properties props;

        private PropertyPlaceholderConfigurerResolver(Properties props) {
            this.props = props;
        }

        public String resolvePlaceholder(String placeholderName) {
            return props.getProperty(placeholderName);
        }
    }

    // ~ Copied and adapted from {@link PlaceholderConfigurerSupport} -----------------------------------------------------------

    protected void doProcessProperties(ConfigurableListableBeanFactory beanFactoryToProcess,
                                       StringValueResolver valueResolver) {
        BeanDefinitionVisitor visitor = new BeanDefinitionVisitor(valueResolver);
        String[] beanNames = beanFactoryToProcess.getBeanDefinitionNames();
        for (String curName : beanNames) {
            BeanDefinition bd = beanFactoryToProcess.getBeanDefinition(curName);
            try {
                visitor.visitBeanDefinition(bd);
            } catch (Exception ex) {
                throw new BeanDefinitionStoreException(bd.getResourceDescription(), curName, ex.getMessage());
            }
        }

        // New in Spring 2.5: resolve placeholders in alias target names and aliases as well.
        beanFactoryToProcess.resolveAliases(valueResolver);

        // New in Spring 3.0: resolve placeholders in embedded values such as annotation attributes.
        beanFactoryToProcess.addEmbeddedValueResolver(valueResolver);
    }


}
