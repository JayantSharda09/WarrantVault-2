package com.example.warrantyvault.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.warrantyvault.R
import com.example.warrantyvault.ProductDetailsActivity
import com.example.warrantyvault.data.ProductRepository
import com.example.warrantyvault.databinding.FragmentDashboardBinding
import com.example.warrantyvault.util.DateUtils
import kotlinx.coroutines.launch

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentDashboardBinding.bind(view)

        binding.recyclerRecent.layoutManager = LinearLayoutManager(requireContext())
        binding.btnDashboardAdd.setOnClickListener {
            startActivity(Intent(requireContext(), com.example.warrantyvault.AddProductActivity::class.java))
        }
        loadData()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) loadData()
    }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch {
            val products = ProductRepository.getAll()
            if (_binding == null) return@launch

            val active = products.count { DateUtils.daysRemaining(it.expiryDate) >= 0 }
            val expiring = products.count {
                val days = DateUtils.daysRemaining(it.expiryDate)
                days in 0..30
            }

            binding.tvTotal.text = products.size.toString()
            binding.tvActive.text = active.toString()
            binding.tvExpiring.text = expiring.toString()

            val recent = products.take(3)

            binding.recyclerRecent.adapter = ProductAdapter(recent) { product ->
                startActivity(
                    Intent(requireContext(), ProductDetailsActivity::class.java)
                        .putExtra(ProductDetailsActivity.EXTRA_PRODUCT_ID, product.id)
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
