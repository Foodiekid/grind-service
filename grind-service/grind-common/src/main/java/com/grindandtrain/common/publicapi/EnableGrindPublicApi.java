package com.grindandtrain.common.publicapi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

/**
 * Put on a service's application class to serve the public API under {@code /grind/api}: Cloudflare edge check,
 * client-version check, size limit, deprecation headers, Supabase JWT security and CORS, configured under
 * {@code grind.public-api}. See {@link PublicApiConfiguration}.
 *
 * @author Dheeraj_Edupuganti
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(PublicApiConfiguration.class)
public @interface EnableGrindPublicApi {
}
