package com.subatomicplanets.shield;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.ContactViewHolder> {
    private final List<String> contactNames;
    private final List<String> contactNumbers;
    private final Context context;
    private final Intent chatIntent;

    public ContactAdapter(Context context, List<String> contactNames, List<String> contactNumbers) {
        this.context = context;
        this.contactNames = contactNames;
        this.contactNumbers = contactNumbers;
        this.chatIntent = new Intent(context, ChatActivity.class);
    }

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, parent, false);
        return new ContactViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ContactViewHolder holder, int position) {
        String contactName = contactNames.get(position);
        String contactNumber = contactNumbers.get(position);
        holder.contactNameTextView.setText(contactName);
        holder.itemView.setOnClickListener(v -> {
            chatIntent.putExtra("contactName", contactName);
            chatIntent.putExtra("contactNumber", contactNumber);
            context.startActivity(chatIntent);
        });
    }

    @Override
    public int getItemCount() {
        return contactNames.size();
    }

    static class ContactViewHolder extends RecyclerView.ViewHolder {
        TextView contactNameTextView;

        ContactViewHolder(View itemView) {
            super(itemView);
            contactNameTextView = itemView.findViewById(android.R.id.text1);
        }
    }
}