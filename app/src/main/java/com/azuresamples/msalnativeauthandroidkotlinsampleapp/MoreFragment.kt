package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentMoreBinding

class MoreFragment : Fragment() {
    private var _binding: FragmentMoreBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentMoreBinding.inflate(inflater, container, false)
        val view = binding.root

        init()

        return view
    }

    private fun init() {
        initializeButtonListener()
    }

    private fun initializeButtonListener() {
        binding.webFallback.setOnClickListener { navigateTo(WebFallbackFragment()) }
        binding.idpWebFlow.setOnClickListener { navigateTo(IdPSignInSignUpWebFragment()) }
        binding.useAccessToken.setOnClickListener { navigateTo(AccessApiFragment()) }
        binding.emailMFA.setOnClickListener { navigateTo(MFAFragment()) }

        // Moved here from the bottom nav once Home/LoginFragment became the app's primary
        // branded sign-in entry point - still the same scenario screens, unchanged.
        binding.emailOobSisu.setOnClickListener { navigateTo(EmailSignInSignUpFragment()) }
        binding.emailPasswordSisu.setOnClickListener { navigateTo(EmailPasswordSignInSignUpFragment()) }
        binding.emailAttributeOobSisu.setOnClickListener { navigateTo(EmailAttributeSignUpFragment()) }
        binding.emailOobSspr.setOnClickListener { navigateTo(PasswordResetFragment()) }
    }

    private fun navigateTo(fragment: Fragment) {
        requireActivity().supportFragmentManager
            .beginTransaction()
            .setReorderingAllowed(true)
            .addToBackStack(fragment::class.java.name)
            .replace(R.id.scenario_fragment, fragment)
            .commit()
    }
}
