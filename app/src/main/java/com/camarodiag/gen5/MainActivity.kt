package com.example.camarodiaggen5

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.camarodiaggen5.ui.theme.CamaroDiagTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Active l'affichage Edge-to-Edge (derrière les barres système)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        setContent {
            CamaroDiagTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    // Le Scaffold gère automatiquement les espaces de la barre d'état (haut) 
    // et de la barre de navigation (bas) grâce aux insets.
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            // Votre barre de menu ou de navigation personnalisée en bas
            // Elle se positionnera proprement juste au-dessus des boutons tactiles Android.
            MyBottomBar()
        }
    ) { innerPadding ->
        // Le contenu de votre application, protégé contre les superpositions
        // grâce au padding fourni par le Scaffold.
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Placez ici le contenu de votre écran actif (ex: diagnostic OBD, compteurs, etc.)
            Text(text = "CamaroDiag Gen5 - Interface prête")
        }
    }
}

@Composable
fun MyBottomBar() {
    // Exemple de composant pour votre menu du bas
    androidx.compose.material3.NavigationBar {
        // Vos éléments de navigation (NavigationBarItem)
    }
}
