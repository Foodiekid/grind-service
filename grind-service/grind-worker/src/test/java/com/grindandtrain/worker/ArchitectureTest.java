package com.grindandtrain.worker;

import com.grindandtrain.common.testkit.ArchitectureRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Checks grind-worker's entry points against the structure rules: each one uses only its own feature, through services.
 *
 * @author Dheeraj_Edupuganti
 */
// Only this module's classes: grind-core checks its own rules, and test jars are on this classpath.
@AnalyzeClasses(packages = "com.grindandtrain.worker", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule entryPointsUseOnlyTheirOwnFeature = ArchitectureRules.ENTRY_POINTS_USE_ONLY_THEIR_OWN_FEATURE;
    @ArchTest
    static final ArchRule entryPointsDoNotTouchInfrastructure = ArchitectureRules.ENTRY_POINTS_DO_NOT_TOUCH_INFRASTRUCTURE;
}
