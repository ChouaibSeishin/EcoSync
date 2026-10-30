package io.ecosync;
import com.tngtech.archunit.core.importer.*;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.*;
class ArchitectureTest {
    @Test void modulesHaveNoCyclesAndPersistenceStaysBehindModuleInterfaces() {
        var classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("io.ecosync");
        slices().matching("io.ecosync.(*)..").should().beFreeOfCycles().check(classes);
        noClasses().that().resideInAPackage("..observability..").should()
            .dependOnClassesThat().resideInAPackage("..identity..").check(classes);
        classes().that().haveSimpleNameEndingWith("Repository").should().notBePublic().check(classes);
    }
}
