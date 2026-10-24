
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddProductActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddProductBinding

    private var purchaseDate = System.currentTimeMillis()
    private var invoicePath: String? = null
    private var originalInvoicePath: String? = null
    private var editingProduct: Product? = null
    private var isSaving = false
    private var isProcessingOcr = false

    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val invoicePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                try {
                    val savedPath = FileUtils.copyUriToInternalStorage(this, uri)

                    if (savedPath.isNullOrBlank()) {
                        Toast.makeText(
                            this,
                            "Could not save invoice image",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        invoicePath = savedPath
                        binding.tvInvoice.text = "Invoice selected — reading text..."
                        runOcr(savedPath)
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        this,
                        "Could not open invoice: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
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

    private fun runOcr(path: String) {
        if (isProcessingOcr) return

        val file = File(path)

        if (!file.exists()) {
            binding.tvInvoice.text = "Invoice image not found"
            return
        }

        isProcessingOcr = true
        binding.btnChooseInvoice.isEnabled = false

        try {
            val image = InputImage.fromFilePath(
                this,
                Uri.fromFile(file)
            )

            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val extractedText = visionText.text

                    if (extractedText.isBlank()) {
                        binding.tvInvoice.text =
                            "No readable text found. Try a clearer image."
                        Toast.makeText(
                            this,
                            "OCR could not find readable text",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        fillFieldsFromOcr(extractedText)

                        binding.tvInvoice.text = "Invoice scanned successfully"

                        Toast.makeText(
                            this,
                            "Invoice text extracted. Please verify the fields.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                .addOnFailureListener { exception ->
                    binding.tvInvoice.text = "Invoice selected — OCR failed"

                    Toast.makeText(
                        this,
                        "OCR failed: ${exception.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                .addOnCompleteListener {
                    isProcessingOcr = false
                    binding.btnChooseInvoice.isEnabled = true
                }
        } catch (e: Exception) {
            isProcessingOcr = false
            binding.btnChooseInvoice.isEnabled = true

            Toast.makeText(
                this,
                "Could not process image: ${e.localizedMessage}",
                Toast.LENGTH_LONG
            ).show()
        }
    }


    private fun fillFieldsFromOcr(text: String) {
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // Detect the five-line invoice format:
        // Product Name
        // Brand
        // Price
        // Purchase Date
        // Warranty Duration

        val datePattern = Regex(
            """^\d{1,2}[/.-]\d{1,2}[/.-]\d{4}$"""
        )

        val numberPattern = Regex(
            """^(?:₹|Rs\.?|INR)?\s*[\d,]+(?:\.\d{1,2})?$""",
            RegexOption.IGNORE_CASE
        )

        // Use positional parsing only for the expected five-line format.
        if (
            lines.size == 5 &&
            lines[0].any { it.isLetter() } &&
            lines[1].any { it.isLetter() } &&
            numberPattern.matches(lines[2]) &&
            datePattern.matches(lines[3]) &&
            lines[4].matches(Regex("""\d{1,3}"""))
        ) {
            binding.etProductName.setText(lines[0])
            binding.etBrand.setText(lines[1])

            val price = lines[2]
                .replace(Regex("""[^\d.]"""), "")
                .toDoubleOrNull()

            if (price != null) {
                binding.etPrice.setText(
                    if (price % 1.0 == 0.0) {
                        price.toLong().toString()
                    } else {
                        price.toString()
                    }
                )
            }

            val date = parseSimpleOcrDate(lines[3])

            if (date != null) {
                purchaseDate = date
                binding.tvPurchaseDate.text =
                    "Purchase Date: ${DateUtils.format(purchaseDate)}"
            }

            binding.etWarrantyMonths.setText(lines[4])
            return
        }

        // Fallback for regular invoices containing labels.
        extractLabeledValue(
            lines,
            listOf("product name", "item name", "product")
        )?.let {
            binding.etProductName.setText(it)
        }

        extractLabeledValue(
            lines,
            listOf("brand", "manufacturer", "make")
        )?.let {
            binding.etBrand.setText(it)
        }

        extractPrice(lines)?.let {
            binding.etPrice.setText(it.toString())
        }

        extractPurchaseDate(lines)?.let {
            purchaseDate = it
            binding.tvPurchaseDate.text =
                "Purchase Date: ${DateUtils.format(purchaseDate)}"
        }

        extractWarrantyMonths(text)?.let {
            binding.etWarrantyMonths.setText(it.toString())
        }
    }

    private fun parseSimpleOcrDate(value: String): Long? {
        val formats = listOf(
            "dd/MM/yyyy",
            "d/M/yyyy",
            "dd-MM-yyyy",
            "d-M-yyyy",
            "dd.MM.yyyy"
        )

        for (format in formats) {
            try {
                val parser = SimpleDateFormat(
                    format,
                    Locale.ENGLISH
                ).apply {
                    isLenient = false
                }

                val parsed = parser.parse(value) ?: continue
                return parsed.time
            } catch (_: Exception) {
                // Try another date format.
            }
        }

        return null
    }


    private fun extractLabeledValue(
        lines: List<String>,
        labels: List<String>
    ): String? {
        for (i in lines.indices) {
            val line = lines[i]

            for (label in labels) {
                val pattern = Regex(
                    "^\\s*${Regex.escape(label)}\\s*[:\\-]\\s*(.+)$",
                    RegexOption.IGNORE_CASE
                )

                val match = pattern.find(line)

                if (match != null) {
                    return match.groupValues[1]
                        .trim()
                        .takeIf { it.isNotBlank() }
                }

                // Also handle invoices with the label on a separate line.
                if (line.equals(label, ignoreCase = true) &&
                    i + 1 < lines.size
                ) {
                    return lines[i + 1].trim()
                }
            }
        }

        return null
    }

    private fun extractPrice(lines: List<String>): Double? {
        val priceLabels = listOf(
            "total amount",
            "grand total",
            "amount paid",
            "total",
            "price",
            "amount",
            "mrp"
        )

        // Prefer amounts on lines explicitly labelled as price/total.
        for (line in lines) {
            if (Regex(
                    "\\b(date|purchase date|invoice date|warranty)\\b",
                    RegexOption.IGNORE_CASE
                ).containsMatchIn(line)
            ) {
                continue
            }

            if (priceLabels.any {
                    Regex(
                        "\\b${Regex.escape(it)}\\b",
                        RegexOption.IGNORE_CASE
                    ).containsMatchIn(line)
                }
            ) {
                val amountMatch = Regex(
                    "(?:₹|rs\\.?\\s*|inr\\s*)?([0-9][0-9,]*(?:\\.\\d{1,2})?)",
                    RegexOption.IGNORE_CASE
                ).findAll(line).lastOrNull()

                val amount = amountMatch?.groupValues?.get(1)
                    ?.replace(",", "")
                    ?.toDoubleOrNull()

                if (amount != null && amount > 0) {
                    return amount
                }
            }
        }

        // Fallback: look for a currency symbol or currency abbreviation.
        for (line in lines) {
            val match = Regex(
                "(?:₹|\\bINR\\b|\\bRs\\.?\\s*)([0-9][0-9,]*(?:\\.\\d{1,2})?)",
                RegexOption.IGNORE_CASE
            ).find(line)

            val amount = match?.groupValues?.get(1)
                ?.replace(",", "")
                ?.toDoubleOrNull()

            if (amount != null && amount > 0) {
                return amount
            }
        }

        return null
    }

    private fun extractPurchaseDate(lines: List<String>): Long? {
        val dateFormats = listOf(
            "dd/MM/yyyy",
            "d/M/yyyy",
            "dd-MM-yyyy",
            "d-M-yyyy",
            "yyyy-MM-dd",
            "dd.MM.yyyy",
            "d MMM yyyy",
            "dd MMM yyyy",
            "d MMMM yyyy",
            "dd MMMM yyyy"
        )

        val dateRegex = Regex(
            """\b(?:\d{1,2}[/-]\d{1,2}[/-]\d{4}|\d{4}-\d{2}-\d{2}|\d{1,2}\.\d{1,2}\.\d{4}|\d{1,2}\s+[A-Za-z]{3,9}\s+\d{4})\b"""
        )

        val candidates = lines.filter { line ->
            Regex(
                "\\b(purchase date|date of purchase|invoice date|bill date|date)\\b",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(line)
        } + lines

        for (line in candidates.distinct()) {
            val match = dateRegex.find(line) ?: continue
            val dateText = match.value

            for (format in dateFormats) {
                try {
                    val formatter = SimpleDateFormat(
                        format,
                        Locale.ENGLISH
                    ).apply {
                        isLenient = false
                    }

                    val parsed = formatter.parse(dateText) ?: continue
                    val calendar = Calendar.getInstance().apply {
                        time = parsed
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    return calendar.timeInMillis
                } catch (_: Exception) {
                    // Try the next supported date format.
                }
            }
        }

        return null
    }

    private fun extractWarrantyMonths(text: String): Int? {
        val normalized = text
            .replace("warrenty", "warranty", ignoreCase = true)
            .replace("guarantee period", "warranty", ignoreCase = true)

        // Examples: Warranty: 6 months, Warranty - 1 year.
        val labelledPattern = Regex(
            """(?:warranty|guarantee)\s*(?:period|duration)?\s*[:\-]?\s*(\d{1,3})\s*(months?|mos?|years?|yrs?|yr)\b""",
            RegexOption.IGNORE_CASE
        )

        val labelledMatch = labelledPattern.find(normalized)

        if (labelledMatch != null) {
            val number = labelledMatch.groupValues[1].toIntOrNull()
            val unit = labelledMatch.groupValues[2]

            if (number != null && number > 0) {
                return if (unit.startsWith("y", true)) {
                    number * 12
                } else {
                    number
                }
            }
        }

        // Also handle formats such as "6 months warranty".
        val reversePattern = Regex(
            """\b(\d{1,3})\s*(months?|mos?|years?|yrs?|yr)\s*(?:of\s*)?(?:warranty|guarantee)\b""",
            RegexOption.IGNORE_CASE
        )

        val reverseMatch = reversePattern.find(normalized)

        if (reverseMatch != null) {
            val number = reverseMatch.groupValues[1].toIntOrNull()
            val unit = reverseMatch.groupValues[2]

            if (number != null && number > 0) {
                return if (unit.startsWith("y", true)) {
                    number * 12
                } else {
                    number
                }
            }
        }

        return null
    }

    private fun fillUnlabeledProductFields(
        lines: List<String>,
        extractedBrand: String?,
        extractedProduct: String?
    ) {
        val ignored = Regex(
            """(invoice|bill|receipt|date|price|amount|total|warranty|guarantee|phone|email|address|gst|tax|qty|quantity|₹|INR|Rs\.?)""",
            RegexOption.IGNORE_CASE
        )

        val candidates = lines.filter { line ->
            line.length >= 2 &&
                    !ignored.containsMatchIn(line) &&
                    !Regex("""^\d[\d\s/.,-]*$""").matches(line) &&
                    !Regex("""^\d{1,2}\s+[A-Za-z]{3,9}\s+\d{4}$""").matches(line)
        }

        if (extractedBrand.isNullOrBlank() &&
            binding.etBrand.text.isNullOrBlank()
        ) {
            candidates.firstOrNull()?.let {
                binding.etBrand.setText(it)
            }
        }

        if (extractedProduct.isNullOrBlank() &&
            binding.etProductName.text.isNullOrBlank()
        ) {
            val candidate = candidates.firstOrNull {
                it != binding.etBrand.text.toString()
            }

            candidate?.let {
                binding.etProductName.setText(it)
            }
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
            binding.etWarrantyMonths.setText(
                product.warrantyMonths.toString()
            )

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
        val price = binding.etPrice.text.toString()
            .trim().toDoubleOrNull()
        val months = binding.etWarrantyMonths.text.toString()
            .trim().toIntOrNull()

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
            binding.etWarrantyMonths.error =
                "Enter warranty duration in months"
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
                if (
                    existing != null &&
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
                if (
                    invoicePath != originalInvoicePath &&
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

    override fun onDestroy() {
        textRecognizer.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PRODUCT_ID = "edit_product_id"
    }
}
