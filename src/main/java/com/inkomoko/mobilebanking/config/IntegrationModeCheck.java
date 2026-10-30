package com.inkomoko.mobilebanking.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Refuses to start with a core-banking or payment mode that has no real backend.
 * Customer and loan data always comes from core banking; there is no demo or mock mode.
 * Runs before any bean is created so the error names the setting instead of a missing bean.
 */
@Component
public class IntegrationModeCheck implements BeanFactoryPostProcessor, EnvironmentAware {

    private static final Set<String> MODES = Set.of("omnichannel", "live");

    private Environment env;

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        check("inkomoko.fineract.mode", "FINERACT_MODE");
        check("inkomoko.payment.mode", "PAYMENT_MODE");
    }

    private void check(String property, String variable) {
        String mode = env.getProperty(property, "omnichannel").trim().toLowerCase();
        if (!MODES.contains(mode)) {
            throw new IllegalStateException(variable + "=" + mode + " is not supported. Use omnichannel (default) or live;"
                    + " demo and mock modes were removed so that all customer and loan data comes from core banking.");
        }
    }
}
