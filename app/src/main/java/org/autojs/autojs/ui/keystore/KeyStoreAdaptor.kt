package org.autojs.autojs.ui.keystore

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.apkbuilder.keystore.KeyStore
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ItemKeyStoreBinding

class KeyStoreAdaptor(
    private val keyStoreAdapterCallback: KeyStoreAdapterCallback,
) : ListAdapter<KeyStore, KeyStoreAdaptor.KeyStoreViewHolder>(KeyStoreDiffCallback()) {

    class KeyStoreDiffCallback : DiffUtil.ItemCallback<KeyStore>() {
        override fun areItemsTheSame(oldItem: KeyStore, newItem: KeyStore): Boolean {
            return oldItem.absolutePath == newItem.absolutePath
        }

        override fun areContentsTheSame(oldItem: KeyStore, newItem: KeyStore): Boolean {
            return oldItem.filename == newItem.filename &&
                    oldItem.password == newItem.password &&
                    oldItem.alias == newItem.alias &&
                    oldItem.aliasPassword == newItem.aliasPassword &&
                    oldItem.verified == newItem.verified
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KeyStoreViewHolder {
        val binding = ItemKeyStoreBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return KeyStoreViewHolder(binding).apply {
            binding.delete.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    keyStoreAdapterCallback.onDeleteButtonClicked(getItem(bindingAdapterPosition))
                }
            }
            binding.verify.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    keyStoreAdapterCallback.onVerifyButtonClicked(getItem(bindingAdapterPosition))
                }
            }
            itemView.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    keyStoreAdapterCallback.onVerifyButtonClicked(getItem(bindingAdapterPosition))
                }
            }
        }
    }

    override fun onBindViewHolder(holder: KeyStoreViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class KeyStoreViewHolder(private val binding: ItemKeyStoreBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: KeyStore) {
            binding.apply {
                filename.text = itemView.context.getString(
                    R.string.text_str_colon_space_str_formatter,
                    itemView.context.getString(R.string.text_file_name),
                    item.filename
                )
                alias.text = itemView.context.getString(
                    R.string.text_str_colon_space_str_formatter,
                    itemView.context.getString(R.string.text_key_alias),
                    item.alias
                )
                verify.setImageResource(
                    if (item.verified) R.drawable.ic_key_store_verified else R.drawable.ic_key_store_unverified
                )
            }
        }
    }

    interface KeyStoreAdapterCallback {
        fun onDeleteButtonClicked(keyStore: KeyStore)
        fun onVerifyButtonClicked(keyStore: KeyStore)
    }
}
