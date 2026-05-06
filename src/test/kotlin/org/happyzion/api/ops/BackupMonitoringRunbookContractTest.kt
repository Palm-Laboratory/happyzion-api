package org.happyzion.api.ops

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class BackupMonitoringRunbookContractTest {

    @Test
    fun `backup script should create gzipped custom format pg dumps with configurable environment`() {
        val script = readDeployScript("backup-db.sh")

        assertThat(script).contains("pg_dump")
        assertThat(script).containsPattern("""(?s)pg_dump\b.*--format=custom""")
        assertThat(script).containsPattern("""(?s)pg_dump\b.*\|\s*gzip\b""")
        assertThat(script).containsPattern("""db-\$\([^)]*date\s+\+%Y%m%d-%H%M%S[^)]*\)\.dump\.gz""")
        assertThat(script).containsPattern("""(?m)^\s*BACKUP_DIR=\$\{[A-Z0-9_]+:-[^}]+}\s*$""")
        assertThat(script).containsPattern("""(?m)^\s*(?:PGHOST|PGPORT|PGDATABASE|PGUSER|PGPASSWORD|DATABASE_URL)=""")
    }

    @Test
    fun `disk check script should monitor upload volume with default threshold and fail when exceeded`() {
        val script = readDeployScript("check-disk.sh")

        assertThat(script).containsPattern("""(?m)^\s*(?:THRESHOLD|DISK_THRESHOLD)=\$\{[A-Z0-9_]+:-80}\s*$""")
        assertThat(script).containsPattern("""(?m)^\s*(?:UPLOAD_DIR|CHECK_PATH|TARGET_PATH)=\$\{[A-Z0-9_]+:-/opt/happyzion/uploads}\s*$""")
        assertThat(script).containsPattern("""\bdf\b""")
        assertThat(script).containsPattern("""(?s)if\s+.*(?:-ge|-gt)\s+["']?\$\{?(?:THRESHOLD|DISK_THRESHOLD)}?["']?.*exit\s+1""")
    }

    @Test
    fun `backup restore runbook should document restore monitoring cron monthly upload query and draft GC caveat`() {
        val runbook = Files.readString(Path.of("docs/db-backup-restore-runbook.md"))
        val normalized = runbook.lowercase()

        assertThat(normalized).contains("backup-db.sh")
        assertThat(normalized).contains(".dump.gz")
        assertThat(normalized).contains("gunzip -c")
        assertThat(normalized).contains("| pg_restore")
        assertThat(normalized).contains("cron")
        assertThat(normalized).contains("check-disk.sh")
        assertThat(normalized).contains("post_asset")
        assertThat(normalized).contains("byte_size")
        assertThat(normalized).contains("date_trunc('month', created_at)")
        assertThat(normalized).contains("draft")
        assertThat(normalized).contains("excluded from gc")
    }

    private fun readDeployScript(fileName: String): String {
        val path = Path.of("deploy/scripts", fileName)
        assertThat(path).exists()
        return Files.readString(path)
    }
}
