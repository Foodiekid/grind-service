package com.grindandtrain.core;

import com.grindandtrain.common.testkit.ArchitectureRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Checks grind-core against the structure rules: features are self-contained and free of cycles.
 *
 * @author Dheeraj_Edupuganti
 */
@AnalyzeClasses(packages = "com.grindandtrain.core", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule featuresHaveNoCycles = ArchitectureRules.FEATURES_HAVE_NO_CYCLES;
    @ArchTest
    static final ArchRule commonDoesNotDependOnFeatures = ArchitectureRules.CORE_COMMON_DOES_NOT_DEPEND_ON_FEATURES;
    @ArchTest
    static final ArchRule repositoriesArePrivate = ArchitectureRules.REPOSITORIES_ARE_PRIVATE_TO_THEIR_FEATURE;
    @ArchTest
    static final ArchRule coreDoesNotUseApiDtos = ArchitectureRules.CORE_DOES_NOT_USE_API_DTOS;
    @ArchTest
    static final ArchRule servicesInServicePackages = ArchitectureRules.SERVICES_LIVE_IN_SERVICE_PACKAGES;
    @ArchTest
    static final ArchRule repositoriesInRepositoryPackages = ArchitectureRules.REPOSITORIES_LIVE_IN_REPOSITORY_PACKAGES;
}
