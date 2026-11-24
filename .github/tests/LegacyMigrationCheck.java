import com.telehop.common.db.DatabaseConfig;
import com.telehop.common.db.DatabaseManager;
import java.sql.SQLException;

/** Uses the packaged JDBC driver against a disposable partially migrated homes table. */
class LegacyMigrationCheck {
    public static void main(String[] args) throws Exception {
        var config = new DatabaseConfig("127.0.0.1", Integer.parseInt(System.getenv("MYSQL_PORT")),
                "telehop_ci", "telehop", System.getenv("MYSQL_PWD"), 2);
        try {
            DatabaseManager.open(config).shutdown();
        } catch (SQLException failure) {
            if (failure.getErrorCode() != 1062) throw failure;
            System.out.println("Duplicate home names stopped the migration");
            return;
        }
        throw new AssertionError("Conflicting names should preserve the legacy primary key and stop startup");
    }
}
