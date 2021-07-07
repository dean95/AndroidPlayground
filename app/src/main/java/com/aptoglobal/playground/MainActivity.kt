package com.aptoglobal.playground

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.aptoglobal.playground.core.util.doIfSdk29OrUpOrNull
import com.aptoglobal.playground.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val internalPhotosAdapter by lazy { InternalStoragePhotoAdapter(::onPhotoClick) }
    private val externalPhotosAdapter by lazy { SharedPhotoAdapter {} }

    private var readPermissionGranted = false
    private var writePermissionGranted = false

    private lateinit var permissionsLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var contentObserver: ContentObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        permissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            readPermissionGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: readPermissionGranted
            writePermissionGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: writePermissionGranted

            if (readPermissionGranted) {
                renderPhotosFromExternalStorage()
            } else {
                Toast.makeText(this, "Can't read files without permission", Toast.LENGTH_LONG).show()
            }
        }
        updateOrRequestPermissions()

        val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) {
            lifecycleScope.launch {
                val isPrivate = binding.switchPrivate.isChecked
                val isSavedSuccessfully = when {
                    isPrivate -> savePhotoToInternalStorage(UUID.randomUUID().toString(), it)
                    writePermissionGranted -> savePhotoToExternalStorage(UUID.randomUUID().toString(), it)
                    else -> false
                }
                if (isPrivate) {
                    renderPhotosFromInternalStorage()
                }
                if (isSavedSuccessfully) {
                    Toast.makeText(this@MainActivity, "Photo saved successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Failed to save photo", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnTakePhoto.setOnClickListener {
            takePhoto.launch()
        }

        initializeInternalPhotosRecyclerView()
        renderPhotosFromInternalStorage()

        initializeExternalPhotosRecyclerView()
        initContentObserver()
        renderPhotosFromExternalStorage()
    }

    private fun initContentObserver() {
        contentObserver = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                if (readPermissionGranted) {
                    renderPhotosFromExternalStorage()
                }
            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
    }

    private fun renderPhotosFromExternalStorage() {
        lifecycleScope.launch {
            val photos = loadPhotosFromExternalStorage()
            externalPhotosAdapter.submitList(photos)
        }
    }

    private fun initializeExternalPhotosRecyclerView() = binding.rvPublicPhotos.run {
        adapter = externalPhotosAdapter
        layoutManager = StaggeredGridLayoutManager(3, RecyclerView.VERTICAL)
    }

    private suspend fun loadPhotosFromExternalStorage(): List<SharedStoragePhoto> {
        return withContext(Dispatchers.IO) {
            val collection = doIfSdk29OrUpOrNull {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val photos = mutableListOf<SharedStoragePhoto>()
            contentResolver.query(
                collection,
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.WIDTH,
                    MediaStore.Images.Media.HEIGHT
                ),
                null,
                null,
                "${MediaStore.Images.Media.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val displayName = cursor.getString(displayNameColumn)
                    val width = cursor.getInt(widthColumn)
                    val height = cursor.getInt(heightColumn)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    photos.add(SharedStoragePhoto(id, displayName, width, height, contentUri))
                }
            }
            photos
        }
    }

    private fun updateOrRequestPermissions() {
        val hasReadPermission =
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        val hasWritePermission =
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        val minSdk29 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        readPermissionGranted = hasReadPermission
        writePermissionGranted = hasWritePermission || minSdk29

        val permissionsToRequest = mutableListOf<String>()
        if (!writePermissionGranted) {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (!readPermissionGranted) {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (permissionsToRequest.isNotEmpty()) {
            permissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    @SuppressLint("InlinedApi")
    private suspend fun savePhotoToExternalStorage(displayName: String, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        val imageCollection = doIfSdk29OrUpOrNull {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.WIDTH, bitmap.width)
            put(MediaStore.Images.Media.HEIGHT, bitmap.height)
        }

        try {
            contentResolver.insert(imageCollection, contentValues)?.also { uri ->
                contentResolver.openOutputStream(uri).use { outputStream ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)) {
                        throw IOException("Couldn't save bitmap")
                    }
                }
            } ?: throw IOException("Couldn't create MediaStore store entry")
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    private fun initializeInternalPhotosRecyclerView() = binding.rvPrivatePhotos.run {
        adapter = internalPhotosAdapter
        layoutManager = StaggeredGridLayoutManager(3, RecyclerView.VERTICAL)
    }

    private fun renderPhotosFromInternalStorage() {
        lifecycleScope.launch {
            val photos = loadPhotosFromInternalStorage()
            internalPhotosAdapter.submitList(photos)
        }
    }

    private suspend fun deletePhotoFromInternalStorage(filename: String): Boolean = withContext(Dispatchers.IO) {
        try {
            deleteFile(filename)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun loadPhotosFromInternalStorage(): List<InternalStoragePhoto> {
        return withContext(Dispatchers.IO) {
            val files = filesDir.listFiles()
            files?.filter { it.canRead() && it.isFile && it.name.endsWith(".jpg") }?.map {
                val bytes = it.readBytes()
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                InternalStoragePhoto(it.name, bitmap)
            } ?: emptyList()
        }
    }

    private suspend fun savePhotoToInternalStorage(filename: String, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        try {
            openFileOutput("$filename.jpg", MODE_PRIVATE).use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)) {
                    throw IOException("Couldn't save bitmap.")
                }
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    private fun onPhotoClick(photo: InternalStoragePhoto) = lifecycleScope.launch {
        val isDeletedSuccessfully = deletePhotoFromInternalStorage(photo.name)
        if (isDeletedSuccessfully) {
            renderPhotosFromInternalStorage()
            Toast.makeText(this@MainActivity, "Photo deleted successfully", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this@MainActivity, "Failed to delete photo", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(contentObserver)
        super.onDestroy()
    }
}
