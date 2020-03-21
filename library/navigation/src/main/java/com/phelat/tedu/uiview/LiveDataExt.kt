package com.phelat.tedu.uiview

import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController

inline fun <reified T : Navigate> LiveData<T>.observeNavigation(fragment: Fragment) {
    observe(fragment.viewLifecycleOwner, Observer {
        when (it) {
            is Navigate.ToDirection -> {
                fragment.findNavController().navigate(it.directionId.id, it.bundle)
            }
            is Navigate.Up -> {
                fragment.findNavController().navigateUp()
            }
        }
    })
}