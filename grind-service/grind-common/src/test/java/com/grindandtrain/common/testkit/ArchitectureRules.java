package com.grindandtrain.common.testkit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import java.util.Optional;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

/**
 * The structure rules of the backend, as tests. They keep every feature (account, keyring, sync, upload, ...) a
 * self-contained module that could later be moved into its own service: its tables are reached only through its own
 * repositories, other features use it only through its service and domain types, and features don't depend on each
 * other in cycles.
 * <p>
 * A feature is the package right under a service root ({@code com.grindandtrain.core}, {@code .api},
 * {@code .worker}, {@code .partner}); the same name in core, api and worker is the same feature. {@code common} is
 * shared plumbing, not a feature. Shared with every service through grind-common's test jar; each module's
 * ArchitectureTest checks the rules that apply to its classes.
 *
 * @author Dheeraj_Edupuganti
 */
public final class ArchitectureRules {

    private static final String ROOT = "com.grindandtrain.";
    private static final String COMMON = "common";

    private ArchitectureRules() {
    }

    /** Features may depend on each other (through services), but never in a circle. */
    public static final ArchRule FEATURES_HAVE_NO_CYCLES = slices()
            .matching("com.grindandtrain.core.(*)..")
            .should().beFreeOfCycles()
            .as("Features in grind-core don't depend on each other in cycles");

    /** Shared plumbing knows nothing about features, so any feature can be moved out without it. */
    public static final ArchRule CORE_COMMON_DOES_NOT_DEPEND_ON_FEATURES = noClasses()
            .that().resideInAPackage("com.grindandtrain.core.common..")
            .should(dependOnAFeature())
            .as("grind-core's common package doesn't depend on any feature");

    /** A feature's tables are reached only through its own repositories. */
    public static final ArchRule REPOSITORIES_ARE_PRIVATE_TO_THEIR_FEATURE = classes()
            .that().resideInAPackage("com.grindandtrain.core.*.repository..")
            .should(onlyBeUsedWithinTheirFeature())
            .as("A repository is used only by its own feature");

    /** Business logic never sees the HTTP contract; mappers in the web layer convert. */
    public static final ArchRule CORE_DOES_NOT_USE_API_DTOS = noClasses()
            .that().resideInAPackage("com.grindandtrain.core..")
            .should().dependOnClassesThat().resideInAPackage("com.grindandtrain.contract.api..")
            .as("grind-core doesn't use the generated API types");

    public static final ArchRule SERVICES_LIVE_IN_SERVICE_PACKAGES = classes()
            .that().areAnnotatedWith(Service.class)
            .should().resideInAPackage("..service..")
            .allowEmptyShould(true);

    public static final ArchRule REPOSITORIES_LIVE_IN_REPOSITORY_PACKAGES = classes()
            .that().areAnnotatedWith(Repository.class)
            .should().resideInAPackage("..repository..")
            .allowEmptyShould(true);

    public static final ArchRule CONTROLLERS_LIVE_IN_VERSIONED_WEB_PACKAGES = classes()
            .that().areAnnotatedWith(RestController.class)
            .and().resideInAnyPackage("com.grindandtrain.api..", "com.grindandtrain.partner..")
            .should().resideInAnyPackage("com.grindandtrain.api.*.web.(v*)..", "com.grindandtrain.partner.*.web.(v*)..")
            .allowEmptyShould(true)
            .as("Public API controllers live in <feature>.web.v<n>");

    /** Controllers and event handlers call only their own feature's services, never another feature's. */
    public static final ArchRule ENTRY_POINTS_USE_ONLY_THEIR_OWN_FEATURE = classes()
            .that().resideInAnyPackage("com.grindandtrain.api..", "com.grindandtrain.worker..")
            .should(useOnlyTheirOwnCoreFeature())
            .allowEmptyShould(true)
            .as("Controllers and handlers use only their own feature in grind-core");

    /** Entry points go through services; storage, transactions, repositories and outbound clients are theirs. */
    public static final ArchRule ENTRY_POINTS_DO_NOT_TOUCH_INFRASTRUCTURE = noClasses()
            .that().resideInAnyPackage("com.grindandtrain.api..", "com.grindandtrain.worker..",
                    "com.grindandtrain.partner.*.web..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.grindandtrain.*.*.repository..",
                    "com.grindandtrain.*.*.client..",
                    "com.grindandtrain.core.common.storage..",
                    "com.grindandtrain.core.common.persistence..")
            .allowEmptyShould(true)
            .as("Controllers and handlers don't touch repositories, outbound clients, storage or transactions");

    /** The feature of a class: the package right under a service root; empty for anything else. */
    static Optional<String> featureOf(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        for (String module : new String[] {"core", "api", "worker", "partner"}) {
            String prefix = ROOT + module + ".";
            if (packageName.startsWith(prefix)) {
                String rest = packageName.substring(prefix.length());
                String feature = rest.contains(".") ? rest.substring(0, rest.indexOf('.')) : rest;
                return COMMON.equals(feature) ? Optional.empty() : Optional.of(feature);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> coreFeatureOf(JavaClass javaClass) {
        return javaClass.getPackageName().startsWith(ROOT + "core.") ? featureOf(javaClass) : Optional.empty();
    }

    private static ArchCondition<JavaClass> dependOnAFeature() {
        return new ArchCondition<>("depend on a feature") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    if (coreFeatureOf(dependency.getTargetClass()).isPresent()) {
                        events.add(SimpleConditionEvent.satisfied(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> onlyBeUsedWithinTheirFeature() {
        return new ArchCondition<>("only be used within their own feature") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                Optional<String> feature = coreFeatureOf(item);
                for (Dependency dependency : item.getDirectDependenciesToSelf()) {
                    Optional<String> user = coreFeatureOf(dependency.getOriginClass());
                    if (!user.equals(feature)) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> useOnlyTheirOwnCoreFeature() {
        return new ArchCondition<>("use only their own feature in grind-core") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                Optional<String> feature = featureOf(item);
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    Optional<String> target = coreFeatureOf(dependency.getTargetClass());
                    if (target.isPresent() && !target.equals(feature)) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }
}
