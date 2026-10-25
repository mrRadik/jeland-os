import java.nio.charset.StandardCharsets;
import java.util.Base64;
import ru.jeland.os.data.automation.SeatDeviceMigration;

/** Source-launch helper for test-seat-device-migration.py; no Android runtime required. */
class PrintSeatMigrationSql {
    public static void main(String[] args) {
        String sql = String.join("\n", SeatDeviceMigration.INSTANCE.getStatements());
        System.out.println("SQL64:" + Base64.getEncoder().encodeToString(sql.getBytes(StandardCharsets.UTF_8)));
    }
}
