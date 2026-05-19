package io.jenkins.plugins.unity.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class UnityVersionTest {
    @Test
    fun `parses Unity editor versions with stream suffixes`() {
        assertThat(UnityVersion.tryParse("2022.3.16f1")).isEqualTo(UnityVersion(2022, 3, 16))
        assertThat(UnityVersion.tryParse("6000.0.1b15")).isEqualTo(UnityVersion(6000, 0, 1))
        assertThat(UnityVersion.tryParse("Unity 2021.3.42f1")).isNull()
    }

    @Test
    fun `orders partial and full versions by numeric components`() {
        assertThat(UnityVersion(2022, 3, 16)).isGreaterThan(UnityVersion(2022, 3, 1))
        assertThat(UnityVersion(2023)).isGreaterThan(UnityVersion(2022, 3, 99))
        assertThat(UnityVersion(2022, 3)).isEqualByComparingTo(UnityVersion(2022, 3, 0))
    }
}
