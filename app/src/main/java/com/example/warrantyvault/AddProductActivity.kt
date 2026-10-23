
package com.example.warrantyvault

import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.warrantyvault.data.Product
import com.example.warrantyvault.data.ProductRepository
import com.example.warrantyvault.databinding.ActivityAddProductBinding
import com.example.warrantyvault.util.DateUtils
import com.example.warrantyvault.util.FileUtils
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

class AddProductActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddProductBinding

    private var purchaseDate = System.currentTimeMillis()
    private var invoicePath: String? = null
    private var originalInvoicePath: String? = null
    private var editingProduct: Product? = null
    private var isSaving = false

    private val invoicePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                try {
                    invoicePath = FileUtils.copyUriToInternalStorage(this, uri)
                    binding.tvInvoice.text = "Invoice: New image selected"
                } catch (e: Exception) {
                    Toast.makeText(
                        this,
                        "Could not save invoice image",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAddProductBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvPurchaseDate.setOnClickListener {
            showDatePicker()
        }

        binding.btnChooseInvoice.setOnClickListener {
            invoicePicker.launch(arrayOf("image/*"))
        }

        binding.btnSaveProduct.setOnClickListener {
            saveProduct()
        }

        val id = intent.getStringExtra(EXTRA_PRODUCT_ID)

        if (!id.isNullOrBlank()) {
            binding.btnSaveProduct.text = "Loading..."
            binding.btnSaveProduct.isEnabled = false
            loadProduct(id)
        } else {
            binding.tvPurchaseDate.text =
                "Purchase Date: ${DateUtils.format(purchaseDate)}"
            binding.tvInvoice.text = "Choose an invoice image"
            binding.btnSaveProduct.text = "Save Product"
        }
    }

    private fun loadProduct(id: String) {
        lifecycleScope.launch {
            val product = ProductRepository.getById(id)

            if (product == null) {
                Toast.makeText(
                    this@AddProductActivity,
                    "Product not found",
                    Toast.LENGTH_LONG
                ).show()
                finish()
                return@launch
            }

            editingProduct = product
            originalInvoicePath = product.invoicePath
            invoicePath = product.invoicePath
            purchaseDate = product.purchaseDate

            binding.etProductName.setText(product.productName)
            binding.etBrand.setText(product.brand)
            binding.etPrice.setText(product.price.toString())
            binding.etWarrantyMonths.setText(product.warrantyMonths.toString())

            binding.tvPurchaseDate.text =
                "Purchase Date: ${DateUtils.format(purchaseDate)}"

            binding.tvInvoice.text =
                if (product.invoicePath.isNullOrBlank()) {
                    "Choose an invoice image"
                } else {
                    "Existing invoice saved (choose to replace)"
                }

            binding.btnSaveProduct.text = "Update Product"
            binding.btnSaveProduct.isEnabled = true
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
        if (isSaving) return

        val name = binding.etProductName.text.toString().trim()
        val brand = binding.etBrand.text.toString().trim()
        val price = binding.etPrice.text.toString().trim().toDoubleOrNull()
        val months = binding.etWarrantyMonths.text.toString().trim().toIntOrNull()

        if (name.isEmpty()) {
            binding.etProductName.error = "Enter product name"
            return
        }

        if (brand.isEmpty()) {
            binding.etBrand.error = "Enter brand"
            return
        }

        if (price == null || price < 0) {
            binding.etPrice.error = "Enter a valid price"
            return
        }

        if (months == null || months <= 0) {
            binding.etWarrantyMonths.error = "Enter warranty duration in months"
            return
        }

        val existing = editingProduct

        val product = Product(
            id = existing?.id ?: "",
            productName = name,
            brand = brand,
            price = price,
            purchaseDate = purchaseDate,
            warrantyMonths = months,
            expiryDate = DateUtils.addMonths(purchaseDate, months),
            invoicePath = invoicePath,
            userId = existing?.userId ?: ""
        )

        isSaving = true
        binding.btnSaveProduct.isEnabled = false

        lifecycleScope.launch {
            val success = if (existing == null) {
                ProductRepository.insert(product).isNotBlank()
            } else {
                ProductRepository.update(product)
            }

            if (success) {
                if (existing != null &&
                    originalInvoicePath != invoicePath &&
                    !originalInvoicePath.isNullOrBlank()
                ) {
                    runCatching {
                        File(originalInvoicePath!!).delete()
                    }
                }

                Toast.makeText(
                    this@AddProductActivity,
                    if (existing == null) "Product saved"
                    else "Product updated",
                    Toast.LENGTH_SHORT
                ).show()

                finish()
            } else {
                // Keep the original invoice if saving failed.
                if (invoicePath != originalInvoicePath &&
                    !invoicePath.isNullOrBlank()
                ) {
                    runCatching { File(invoicePath!!).delete() }
                    invoicePath = originalInvoicePath
                }

                isSaving = false
                binding.btnSaveProduct.isEnabled = true

                Toast.makeText(
                    this@AddProductActivity,
                    "Could not save changes. Please try again.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        const val EXTRA_PRODUCT_ID = "edit_product_id"
    }
}
