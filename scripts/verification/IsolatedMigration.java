import org.flywaydb.core.Flyway;

/** Runs the packaged migrations against this task's isolated Compose database only. */
public final class IsolatedMigration {
    public static void main(String[] args) {
        for (String migration : new String[]{
                "116__recovery_scan_health", "117__legacy_import_tracking",
                "118__writeback_identifier_collations"}) {
            String resource = "migration/3.7.2/ddl/V3.7.2_" + migration + ".sql";
            if (IsolatedMigration.class.getClassLoader().getResource(resource) == null) {
                throw new IllegalStateException("Missing packaged migration " + migration);
            }
        }
        if (args.length == 1 && "--artifact-only".equals(args[0])) {
            System.out.println("Packaged migrations 116, 117, 118 found; no database connection attempted.");
            return;
        }
        if (args.length != 1 || !"--migrate-isolated".equals(args[0])) {
            throw new IllegalArgumentException("Use --artifact-only or --migrate-isolated");
        }
        if (!"msp-implementation20261022".equals(System.getenv("VERIFY_PROJECT"))) {
            throw new IllegalStateException("This runner only supports the implementation verification project");
        }
        String password = System.getenv("VERIFY_DB_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("Missing isolated database credential");
        }
        // Same packaged SQL, history table, baseline, encoding and location as the application.
        // Keep validation enabled, as in the acceptance application's environment override.
        Flyway flyway = Flyway.configure()
                .dataSource("jdbc:mysql://mysql:3306/metersphere_quality?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8", "root", password)
                .locations("classpath:migration").table("metersphere_version")
                .baselineOnMigrate(true).baselineVersion("0").encoding("UTF-8")
                .validateOnMigrate(true).load();
        var result = flyway.migrate();
        var info = flyway.info();
        if (info.current() == null || info.pending().length != 0 || !flyway.validateWithResult().validationSuccessful) {
            throw new IllegalStateException("Migration verification incomplete");
        }
        System.out.printf("Isolated migration verified: current=%s, executed=%d, pending=%d%n",
                info.current().getVersion(), result.migrationsExecuted, info.pending().length);
    }
}
