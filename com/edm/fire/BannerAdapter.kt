package com.edm.fire

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.Target
import com.edm.fire.databinding.ItemBannerBinding

class BannerAdapter(private val context: Context) : RecyclerView.Adapter<BannerAdapter.BannerViewHolder>() {

    private var bannerList = mutableListOf<BannerModel>()

    fun setBannerList(banners: Map<String, BannerModel>) {
        bannerList.clear()
        banners.values.forEach { banner ->
            if (banner.imageurl.isNotEmpty()) {
                bannerList.add(banner)
            }
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BannerViewHolder {
        val binding = ItemBannerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BannerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BannerViewHolder, position: Int) {
        holder.bind(bannerList[position])
    }

    override fun getItemCount(): Int = bannerList.size

    inner class BannerViewHolder(private val binding: ItemBannerBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(banner: BannerModel) {

            Glide.with(context)
                .load(banner.imageurl)
                .override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .skipMemoryCache(false)
                .dontTransform()
                .placeholder(R.drawable.coming_soon)
                .error(R.drawable.coming_soon)
                .into(binding.ivBanner)


            binding.root.setOnClickListener {
                if (banner.image_navigation_url.isNotEmpty()) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(banner.image_navigation_url))
                        context.startActivity(intent)
                    } catch (e: Exception) {

                    }
                }
            }
        }
    }
}