package com.soylemis.obdgaraj.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.soylemis.obdgaraj.ui.connect.ConnectScreen
import com.soylemis.obdgaraj.ui.garage.GarageScreen
import com.soylemis.obdgaraj.ui.vehicle.VehicleScreen

object Routes {
    const val GARAGE = "garage"
    const val CONNECT = "connect"
    const val VEHICLE = "vehicle"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.GARAGE) {
        composable(Routes.GARAGE) {
            GarageScreen(
                onOpenConnect = { nav.navigate(Routes.CONNECT) },
                onOpenVehicle = { nav.navigate(Routes.VEHICLE) },
            )
        }
        composable(Routes.CONNECT) {
            ConnectScreen(
                onBack = { nav.popBackStack() },
                onConnected = {
                    nav.navigate(Routes.VEHICLE) { popUpTo(Routes.GARAGE) }
                },
            )
        }
        composable(Routes.VEHICLE) {
            VehicleScreen(
                onBack = { nav.popBackStack(Routes.GARAGE, inclusive = false) },
                onReconnect = { nav.navigate(Routes.CONNECT) },
            )
        }
    }
}
