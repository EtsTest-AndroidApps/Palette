package io.palette.ui.detail

import android.Manifest
import android.arch.lifecycle.ViewModelProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import io.palette.R
import io.palette.data.models.Response
import io.palette.data.models.Unsplash
import io.palette.ui.base.BaseActivity
import io.palette.utility.extentions.*
import io.palette.utility.preference.PreferenceUtility
import kotlinx.android.synthetic.main.activity_detail.*
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.RuntimePermissions
import timber.log.Timber
import javax.inject.Inject

@RuntimePermissions
class DetailActivity @Inject constructor() : BaseActivity() {

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    @Inject lateinit var preferences: PreferenceUtility

    private lateinit var mAdapter: DetailAdapter
    private lateinit var viewModel: DetailViewModel

    private var bitmap: Bitmap? = null
    private var isLiked: Boolean = false

    private lateinit var unsplash: Unsplash

    companion object {
        const val ARG_UNSPLASH = "ARG_UNSPLASH"
        const val ARG_IS_LIKED = "ARG_IS_LIKED"

        fun newInstance(context: Context, unsplash: Unsplash, isLiked: Boolean) = Intent(context, DetailActivity::class.java).apply {
            putExtra(ARG_UNSPLASH, unsplash)
            putExtra(ARG_IS_LIKED, isLiked)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        unsplash = intent.getParcelableExtra(ARG_UNSPLASH)
        isLiked = intent.getBooleanExtra(ARG_IS_LIKED, false)

        viewModel = getViewModel(DetailViewModel::class.java, viewModelFactory)

        mAdapter = DetailAdapter(this, unsplash.user?.name
                ?: "Palette", unsplash.updatedAt, isLiked, preferences.prefShowRGB).apply {
            likePalette = { viewModel.likeUnlikePalette(unsplash, isLiked) }
            savePalette = { savePaletteWithPermissionCheck() }
            sharePalette = { sharePaletteWithPermissionCheck() }
            setWallpaper = { setWallpaperWithPermissionCheck() }
        }
        rvPalette.apply {
            layoutManager = LinearLayoutManager(this@DetailActivity)
            adapter = mAdapter
        }

        observe(viewModel.palette) {
            it ?: return@observe
            when (it.status) {
                Response.Status.LOADING -> {
                }
                Response.Status.SUCCESS -> {
                    it.data ?: return@observe
                    mAdapter.palette = it.data
//                    activityStatus.showContent()
                }
                Response.Status.ERROR -> TODO()
            }
        }

        observe(viewModel.shareUri) {
            it ?: return@observe
            when (it.status) {
                Response.Status.LOADING -> TODO()
                Response.Status.SUCCESS -> {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, it.data)
                        putExtra(Intent.EXTRA_SUBJECT, "Palette")
                    }, "Share palette using"))
                }
                Response.Status.ERROR -> toast("Error sharing palette. Please try again.")
            }
        }

        observe(viewModel.savePalette) {
            it ?: return@observe
            when (it.status) {
                Response.Status.LOADING -> TODO()
                Response.Status.SUCCESS -> toast("Image saved successfully")
                Response.Status.ERROR -> toast("Couldn't save image. Please try again.")
            }
        }

        observe(viewModel.likeUnlikePalette) {
            it ?: return@observe
            when (it.status) {
                Response.Status.LOADING -> {
                    toast("Loading")
                }
                Response.Status.SUCCESS -> {
                    it.data ?: return@observe
                    mAdapter.isLiked = it.data
                }
                Response.Status.ERROR -> {
                    toast("Error in liking palette")
                }
            }
        }

        observe(viewModel.saveWallpaper) {
            it ?: return@observe
            when (it.status) {
                Response.Status.LOADING -> {
                    toast("Loading")
                }
                Response.Status.SUCCESS -> {
                    it.data ?: return@observe
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_ATTACH_DATA).apply {
                        addCategory(Intent.CATEGORY_DEFAULT)
                        setDataAndType(it.data, "image/*")
                        putExtra("mimeType", "image/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Set wallpaper"))
                }
                Response.Status.ERROR -> {
                    toast("Error saving wallpaper. Please try again")
                }
            }
        }

        Glide.with(this)
                .asBitmap()
                .load(unsplash.urls!!.regular)
                .listen(
                        resourceReady = { resource, _, _, _, _ ->
                            resource?.let {
                                ivImage.setImageBitmap(resource)
                                viewModel.generatePalette(resource)
                                bitmap = resource
                            }
                        },
                        loadFailed = { e, _, _, _ ->
                            Timber.e(e, "Glide error occurred!!")
                        }
                )
                .into(ivImage)
        ivImage.maintainAspectRatio(unsplash.width, unsplash.height)

        ivBack.setOnClickListener { onBackPressed() }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onRequestPermissionsResult(requestCode, grantResults)
    }

    @NeedsPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun sharePalette() = viewModel.savePalette(rvPalette, true, bitmap!!)

    @NeedsPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun savePalette() = viewModel.savePalette(rvPalette, false, bitmap!!)

    @NeedsPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun setWallpaper() =
            Glide.with(this)
                    .asBitmap()
                    .load(unsplash.urls?.full)
                    .loadInto(
                            resourceReady = { resource, _ -> viewModel.saveWallpaper(resource) }
                    )
}