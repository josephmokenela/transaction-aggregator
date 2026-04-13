package io.mokenela.transactionaggregator;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the hexagonal (ports-and-adapters) architecture at test time.
 *
 * <p>Rules run on every build and fail immediately when a dependency crosses a layer
 * boundary — providing the same safety net as a type-system constraint but without
 * requiring module separation.</p>
 *
 * <p>Layer overview (innermost → outermost):</p>
 * <pre>
 *   domain      — pure Java, no frameworks, no adapters
 *   application — orchestrates domain via port interfaces only
 *   adapter.in  — HTTP / Kafka inbound; calls application use cases
 *   adapter.out — persistence / external-API outbound; implements domain ports
 *   config      — Spring wiring; may touch all other layers
 * </pre>
 *
 * <p>Uses {@link ClassFileImporter} with {@link ImportOption.DoNotIncludeJars} to
 * restrict scanning to the project's own compiled classes only. This avoids ASM
 * version-mismatch errors when dependency JARs on the classpath contain class files
 * compiled for a newer Java version than ArchUnit's bundled ASM supports.</p>
 */
class HexagonalArchitectureTest {

    private static final String ROOT   = "io.mokenela.transactionaggregator";
    private static final String DOMAIN = ROOT + ".domain..";
    private static final String APP    = ROOT + ".application..";
    private static final String IN     = ROOT + ".adapter.in..";
    private static final String OUT    = ROOT + ".adapter.out..";
    private static final String CONFIG = ROOT + ".config..";

    /**
     * Imported once for the entire test class.
     * {@link ImportOption.DoNotIncludeTests} avoids analysing test helpers.
     * {@link ImportOption.DoNotIncludeJars} limits scanning to target/classes,
     * bypassing dependency JARs that may contain newer bytecode than ASM can parse.
     */
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .withImportOption(new ImportOption.DoNotIncludeJars())
            .importPackages(ROOT);

    // ── 1. Layer dependency rules ──────────────────────────────────────────────

    /**
     * The domain is the innermost hexagon. It must not know about any outer layer —
     * not the application services that orchestrate it, not the adapters that drive it,
     * and certainly not Spring configuration.
     */
    @Test
    void domainMustNotDependOnOuterLayers() {
        noClasses()
                .that().resideInAPackage(DOMAIN)
                .should().dependOnClassesThat().resideInAnyPackage(APP, IN, OUT, CONFIG)
                .because("Domain layer must remain framework-free and self-contained")
                .check(CLASSES);
    }

    /**
     * Application services coordinate domain use cases. They may only depend on
     * domain ports — never on adapter implementations. Spring wires the concrete
     * adapters at runtime via dependency injection.
     */
    @Test
    void applicationMustNotDependOnAdapters() {
        noClasses()
                .that().resideInAPackage(APP)
                .should().dependOnClassesThat().resideInAnyPackage(IN, OUT)
                .because("Application services depend only on domain port interfaces; " +
                         "adapter implementations are supplied by Spring at startup")
                .check(CLASSES);
    }

    /**
     * Inbound adapters (HTTP controllers, Kafka listeners) initiate work by calling
     * application use cases through domain port interfaces. They must never reach into
     * outbound adapters directly — that would bypass the port abstraction and couple
     * two implementation details to each other.
     */
    @Test
    void inboundAdaptersMustNotDependOnOutboundAdapters() {
        noClasses()
                .that().resideInAPackage(IN)
                .should().dependOnClassesThat().resideInAPackage(OUT)
                .because("Inbound adapters must call outbound capabilities through domain port " +
                         "interfaces, not by importing outbound adapter classes directly")
                .check(CLASSES);
    }

    // ── 2. Domain framework purity ────────────────────────────────────────────

    /**
     * Domain classes are plain Java. Spring annotations, reactive types, and
     * persistence mappings belong at the boundaries — never in the domain ring.
     * This keeps the domain independently testable with zero framework overhead.
     */
    @Test
    void domainMustNotUseSpringFramework() {
        noClasses()
                .that().resideInAPackage(DOMAIN)
                .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                .because("Domain classes must be plain Java with no Spring dependencies")
                .check(CLASSES);
    }

    // ── 3. Port implementations must not be public ────────────────────────────

    /**
     * Any class that directly implements a domain port or use-case interface is an
     * adapter or service implementation. Callers depend on the interface, so making
     * the concrete class {@code public} leaks an implementation detail. Package-private
     * visibility signals clearly that the class is not part of the published API.
     */
    @Test
    void portImplementorsMustBePackagePrivate() {
        DescribedPredicate<JavaClass> implementsDomainPort =
                new DescribedPredicate<>("directly implement a domain port or use case interface") {
                    @Override
                    public boolean test(JavaClass javaClass) {
                        return javaClass.getRawInterfaces().stream()
                                .anyMatch(i -> i.getPackageName()
                                        .startsWith(ROOT + ".domain.port"));
                    }
                };

        classes()
                .that(implementsDomainPort)
                .and().areNotInterfaces()
                .should().notBePublic()
                .because("Port implementations are wired by Spring via their interface; " +
                         "marking them public leaks an internal detail that no caller should reference directly")
                .check(CLASSES);
    }

    // ── 4. Naming conventions ─────────────────────────────────────────────────

    /**
     * Inbound port interfaces represent application use cases.
     * The {@code UseCase} suffix makes the intent explicit and distinguishes them
     * from query/command records in the same package.
     */
    @Test
    void inboundPortInterfacesMustEndWithUseCase() {
        classes()
                .that().resideInAPackage(ROOT + ".domain.port.in")
                .and().areInterfaces()
                .should().haveSimpleNameEndingWith("UseCase")
                .because("Use case interfaces should be identifiable by name " +
                         "so that readers immediately understand their role in the hexagonal model")
                .check(CLASSES);
    }

    /**
     * Outbound port interfaces are driven ports — the application's dependency on
     * external systems, expressed as a domain-owned contract.
     * The {@code Port} suffix makes this relationship explicit.
     */
    @Test
    void outboundPortInterfacesMustEndWithPort() {
        classes()
                .that().resideInAPackage(ROOT + ".domain.port.out")
                .and().areInterfaces()
                .should().haveSimpleNameEndingWith("Port")
                .because("Outbound port interfaces represent driven ports; " +
                         "the Port suffix signals that they are owned by the domain, not by any adapter")
                .check(CLASSES);
    }
}
