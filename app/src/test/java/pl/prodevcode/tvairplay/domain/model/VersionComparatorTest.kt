package pl.prodevcode.tvairplay.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {
    @Test fun `numeric segments compare numerically`() {
        assertTrue(VersionComparator.compare("1.10.0", "1.9.9") > 0)
        assertTrue(VersionComparator.compare("1.1.0", "1.1.0") == 0)
        assertTrue(VersionComparator.compare("1.1", "1.1.0") == 0)
        assertTrue(VersionComparator.compare("v1.2.0", "1.1.5") > 0)
    }

    @Test fun `pre-release suffixes are ignored`() {
        assertEquals(0, VersionComparator.compare("1.2.0-rc1", "1.2.0"))
    }

    @Test fun `only numeric versions are releases`() {
        assertTrue(VersionComparator.isRelease("1.0.0"))
        assertFalse(VersionComparator.isRelease("dev"))
        assertFalse(VersionComparator.isRelease(""))
    }
}
