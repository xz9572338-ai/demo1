package com.company.openplatform.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Set;

@AnalyzeClasses(packages = "com.company.openplatform", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {
    private static final Set<String> BUSINESS_MODULES = Set.of(
            "identity", "application", "credential", "permission", "admission",
            "supplychain", "sandbox", "statistics", "audit");

    @ArchTest
    static final ArchRule shared_must_not_depend_on_business_modules = noClasses()
            .that().resideInAPackage("..shared..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..identity..", "..application..", "..credential..", "..permission..",
                    "..admission..", "..supplychain..", "..sandbox..", "..statistics..", "..audit..");

    @ArchTest
    static final ArchRule domain_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..api..", "..application..", "..infrastructure..");

    @ArchTest
    static final ArchRule application_must_not_depend_on_delivery_or_infrastructure = noClasses()
            .that().resideInAnyPackage(
                    "..identity.application..", "..application.application..", "..credential.application..",
                    "..permission.application..", "..admission.application..", "..supplychain.application..",
                    "..sandbox.application..", "..statistics.application..", "..audit.application..")
            .should().dependOnClassesThat().resideInAnyPackage("..api..", "..infrastructure..");

    @ArchTest
    static final ArchRule api_must_not_depend_on_infrastructure = noClasses()
            .that().resideInAPackage("..api..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    @ArchTest
    static void crossModuleDependenciesMustTargetApplicationBoundary(JavaClasses classes) {
        classes.stream().flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                .filter(dependency -> isDifferentBusinessModule(
                        dependency.getOriginClass().getPackageName(), dependency.getTargetClass().getPackageName()))
                .forEach(dependency -> {
                    if (!dependency.getTargetClass().getPackageName().contains(".application")) {
                        throw new AssertionError("Cross-module dependency must target an application boundary: " + dependency);
                    }
                });
    }

    private static boolean isDifferentBusinessModule(String originPackage, String targetPackage) {
        String origin = moduleOf(originPackage);
        String target = moduleOf(targetPackage);
        return origin != null && target != null && !origin.equals(target);
    }

    private static String moduleOf(String packageName) {
        String prefix = "com.company.openplatform.";
        if (!packageName.startsWith(prefix)) {
            return null;
        }
        String candidate = packageName.substring(prefix.length()).split("\\.")[0];
        return BUSINESS_MODULES.contains(candidate) ? candidate : null;
    }
}
