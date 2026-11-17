package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Ibagué T.Q.M. Multiservicios", appName)
  }

  @Test
  fun `ibague geo distance and eta calculation are positive`() {
    val centro = com.example.model.IbagueGeo.zones.first()
    val salado = com.example.model.IbagueGeo.zones.first { it.name.contains("Salado") }
    val km = com.example.model.IbagueGeo.distanceKm(centro.latitude, centro.longitude, salado.latitude, salado.longitude)
    val eta = com.example.model.IbagueGeo.estimateMinutes(km)
    org.junit.Assert.assertTrue(km > 5.0)
    org.junit.Assert.assertTrue(eta >= 15)
  }
}
