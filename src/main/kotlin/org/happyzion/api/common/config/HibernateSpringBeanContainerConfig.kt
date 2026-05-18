package org.happyzion.api.common.config

import org.hibernate.cfg.AvailableSettings
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer
import org.springframework.context.annotation.Configuration
import org.springframework.orm.hibernate5.SpringBeanContainer

@Configuration
class HibernateSpringBeanContainerConfig(
    private val beanFactory: ConfigurableListableBeanFactory,
) : HibernatePropertiesCustomizer {

    override fun customize(hibernateProperties: MutableMap<String, Any>) {
        hibernateProperties[AvailableSettings.BEAN_CONTAINER] = SpringBeanContainer(beanFactory)
    }
}
