package com.covid.nodetrace.ui

import android.Manifest
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.navigation.fragment.findNavController
import com.covid.nodetrace.Contact
import com.covid.nodetrace.ContactManager
import com.covid.nodetrace.R
import com.covid.nodetrace.database.AppDatabase
import com.covid.nodetrace.database.DatabaseFactory
import com.covid.nodetrace.permissions.PermissionHelper
import com.covid.nodetrace.permissions.PermissionRationale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

/**
 * The first screen that a new user of the app sees.
 * It shares some information about the application and how to use it.
 */
class WelcomeFragment : Fragment() {
    private lateinit var deviceID : TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.welcome_screen, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val button = view.findViewById<Button>(R.id.welcome_screen_button)
        button.visibility= View.INVISIBLE

        val display_name = Build.MODEL+Build.ID
        deviceID = view.findViewById(R.id.ID)
        deviceID.setText(display_name)


/*        button.setOnClickListener {
            findNavController().navigate(R.id.health_status_fragment)
        }*/


    }
}