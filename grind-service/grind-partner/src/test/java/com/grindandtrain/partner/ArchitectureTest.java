package com.grindandtrain.partner;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.grindandtrain.common.testkit.ArchitectureRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Checks grind-partner against the structure rules: controllers go through services, and the service stays
 * independent of grind-core (it has no database and must be deployable on its own).
 *
 * @author Dheeraj_Edupuganti
 */
@AnalyzeClasses(packages = "com.grindandtrain.partner", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule entryPointsDoNotTouchInfrastructure = ArchitectureRules.ENTRY_POINTS_DO_NOT_TOUCH_INFRASTRUCTURE;
    @ArchTest
    static final ArchRule controllersInVersionedWebPackages = ArchitectureRules.CONTROLLERS_LIVE_IN_VERSIONED_WEB_PACKAGES;
    @ArchTest
    static final ArchRule servicesInServicePackages = ArchitectureRules.SERVICES_LIVE_IN_SERVICE_PACKAGES;
    @ArchTest
    static final ArchRule independentOfCore = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage("com.grindandtrain.core..", "com.grindandtrain.api..",
                    "com.grindandtrain.worker..")
            .as("grind-partner doesn't depend on the other services or grind-core");
}
