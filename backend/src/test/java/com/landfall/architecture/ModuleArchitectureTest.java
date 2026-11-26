package com.landfall.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.CompositeArchRule.of;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.modules.syntax.ModuleRuleDefinition.modules;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.landfall.LandfallApplication;
import com.landfall.architecture.fixture.common.CommonViolator;
import com.landfall.architecture.fixture.module.alpha.AlphaConsumer;
import com.landfall.architecture.fixture.module.alpha.AlphaViolator;
import com.landfall.architecture.fixture.module.beta.BetaPublicApi;
import com.landfall.architecture.fixture.module.beta.internal.BetaSecret;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

class ModuleArchitectureTest {

    private static final String PRODUCTION_ROOT = "com.landfall";
    private static final String FIXTURE_ROOT = "com.landfall.architecture.fixture";

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(PRODUCTION_ROOT);

    @Test
    void productionClasses_areScannedNonEmpty() {
        assertFalse(PRODUCTION_CLASSES.isEmpty());
        assertTrue(PRODUCTION_CLASSES.contain(LandfallApplication.class));
    }

    @Test
    void productionModules_doNotAccessOtherModulesInternalPackages() {
        noExternalAccessToModuleInternal(PRODUCTION_ROOT).check(PRODUCTION_CLASSES);
    }

    @Test
    void productionModules_areFreeOfCycles() {
        modules()
                .definedByPackages(PRODUCTION_ROOT + ".module.(*)..")
                .should()
                .beFreeOfCycles()
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
        modules()
                .definedByPackages(PRODUCTION_ROOT + ".common.(*)..")
                .should()
                .beFreeOfCycles()
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void controllers_doNotDependOnMappersOrEntities() {
        noClasses()
                .that()
                .areAnnotatedWith(RestController.class)
                .or()
                .areAnnotatedWith(Controller.class)
                .should()
                .dependOnClassesThat(resideInAPackage(PRODUCTION_ROOT + "..")
                        .and(simpleNameEndingWith("Mapper")
                                .or(simpleNameEndingWith("Entity"))
                                .or(annotatedWith("com.baomidou.mybatisplus.annotation.TableName"))))
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void productionClasses_doNotUseFieldInjection() {
        NO_CLASSES_SHOULD_USE_FIELD_INJECTION.allowEmptyShould(true).check(PRODUCTION_CLASSES);
    }

    @Test
    void moduleInternalRule_allowsPublicApiAndSameModuleInternalAccess() {
        JavaClasses allowedFixtures =
                new ClassFileImporter().importClasses(AlphaConsumer.class, BetaPublicApi.class, BetaSecret.class);

        noExternalAccessToModuleInternal(FIXTURE_ROOT).check(allowedFixtures);
    }

    @Test
    void moduleInternalRule_blocksCrossModuleInternalAccess() {
        JavaClasses crossModuleViolation = new ClassFileImporter().importClasses(AlphaViolator.class, BetaSecret.class);

        AssertionError error = assertThrows(AssertionError.class, () -> noExternalAccessToModuleInternal(FIXTURE_ROOT)
                .check(crossModuleViolation));

        assertTrue(error.getMessage().contains("AlphaViolator"), error.getMessage());
        assertTrue(error.getMessage().contains("BetaSecret"), error.getMessage());
    }

    @Test
    void moduleInternalRule_blocksCommonToModuleInternalAccess() {
        JavaClasses commonViolation = new ClassFileImporter().importClasses(CommonViolator.class, BetaSecret.class);

        AssertionError error = assertThrows(AssertionError.class, () -> noExternalAccessToModuleInternal(FIXTURE_ROOT)
                .check(commonViolation));

        assertTrue(error.getMessage().contains("CommonViolator"), error.getMessage());
        assertTrue(error.getMessage().contains("BetaSecret"), error.getMessage());
    }

    private static ArchRule noExternalAccessToModuleInternal(String rootPackage) {
        String modulePackages = rootPackage + ".module..";
        ArchRule crossModuleRule = modules()
                .definedByPackages(rootPackage + ".module.(*)..")
                .should()
                .onlyDependOnEachOtherThroughClassesThat()
                .resideOutsideOfPackage("..internal..")
                .allowEmptyShould(true);
        ArchRule nonModuleRule = noClasses()
                .that()
                .resideOutsideOfPackage(modulePackages)
                .should()
                .dependOnClassesThat(resideInAPackage(modulePackages).and(resideInAPackage("..internal..")))
                .allowEmptyShould(true);
        return of(crossModuleRule).and(nonModuleRule);
    }
}
