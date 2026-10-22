package com.example.warrantyvault

import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.warrantyvault.data.ProductRepository
import com.example.warrantyvault.data.Product
import com.example.warrantyvault.databinding.ActivityAddProductBinding
import com.example.warrantyvault.util.DateUtils
import com.example.warrantyvault.util.FileUtils
import kotlinx.coroutines.launch
import java.util.Calendar

class AddProductActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddProductBinding

    private var purchaseDate: Long = System.currentTimeMillis()
    private var invoicePath: String? = null

    private val invoicePicker =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            if (uri != null) {
                try {
                    invoicePath = FileUtils.copyUriToInternalStorage(this, uri)
                    binding.tvInvoice.text = "Invoice: Selected"
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not save invoice", Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddProductBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvPurchaseDate.text =
            "Purchase Date: ${DateUtils.format(purchaseDate)}"

        binding.tvPurchaseDate.setOnClickListener {
            showDatePicker()
        }

        binding.btnChooseInvoice.setOnClickListener {
            invoicePicker.launch(arrayOf("image/*"))
        }

        binding.btnSaveProduct.setOnClickListener {
            saveProduct()
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = purchaseDate
        }

        DatePickerDialog(
            this,
            { _, year, month, day ->
                val selected = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                purchaseDate = selected.timeInMillis
                binding.tvPurchaseDate.text =
                    "Purchase Date: ${DateUtils.format(purchaseDate)}"
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun saveProduct() {
        val name = binding.etProductName.text.toString().trim()
        val brand = binding.etBrand.text.toString().trim()
        val priceText = binding.etPrice.text.toString().trim()
        val warrantyText = binding.etWarrantyMonths.text.toString().trim()

        if (name.isEmpty()) {
            binding.etProductName.error = "Enter product name"
            return
        }

        if (brand.isEmpty()) {
            binding.etBrand.error = "Enter brand"
            return
        }

        val price = priceText.toDoubleOrNull()
        if (price == null || price < 0) {
            binding.etPrice.error = "Enter a valid price"
            return
        }

        val warrantyMonths = warrantyText.toIntOrNull()
        if (warrantyMonths == null || warrantyMonths <= 0) {
            binding.etWarrantyMonths.error = "Enter warranty in months"
            return
        }

        val expiryDate = DateUtils.addMonths(purchaseDate, warrantyMonths)

        val product = Product(
            productName = name,
            brand = brand,
            price = price,
            purchaseDate = purchaseDate,
            warrantyMonths = warrantyMonths,
            expiryDate = expiryDate,
            invoicePath = invoicePath
        )

        lifecycleScope.launch {
            ProductRepository.insert(product)

            Toast.makeText(
                this@AddProductActivity,
                "Product saved successfully",
                Toast.LENGTH_SHORT
            ).show()

            finish()
        }
    }
}
