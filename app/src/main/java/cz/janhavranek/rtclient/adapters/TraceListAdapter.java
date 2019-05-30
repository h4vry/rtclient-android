package cz.janhavranek.rtclient.adapters;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;

import cz.janhavranek.rtclient.R;
import cz.janhavranek.rtclient.models.Trace;
import cz.janhavranek.rtclient.utils.Iso14443;

public class TraceListAdapter extends RecyclerView.Adapter<TraceListAdapter.ViewHolder> {

    private Context context;
    private List<Trace> data;

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final View view;
        public final ImageView ivSource;
        public final TextView tvAnnotation;
        public final TextView tvBytes;
        public final ImageView ivError;

        public ViewHolder(View v) {
            super(v);
            this.view = v;
            ivSource = v.findViewById(R.id.iv_source);
            tvAnnotation = v.findViewById(R.id.tv_annotation);
            tvBytes = v.findViewById(R.id.tv_bytes);
            ivError = v.findViewById(R.id.iv_error);
        }
    }

    public TraceListAdapter(Context context, List<Trace> data) {
        this.context = context;
        this.data = data;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        View v = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.trace_list_item, viewGroup, false);

        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
        Trace t = data.get(position);

        // set icon
        int iconResourceId = t.isReaderToTag() ? R.drawable.ic_reader_black : R.drawable.ic_card_black;
        Drawable iconDrawable = ResourcesCompat.getDrawable(context.getResources(), iconResourceId, null);
        viewHolder.ivSource.setImageDrawable(iconDrawable);

        // background color
        int color = t.isReaderToTag() ? 0xFFFFFFFF : 0xFFF0F0F0;
        viewHolder.view.setBackgroundColor(color);

        // bytes
        viewHolder.tvBytes.setText(t.getDataAsHexString());

        viewHolder.ivError.setVisibility(t.hasParityError() ? View.VISIBLE : View.GONE);

        String annotation = getAnnotation(position);
        viewHolder.tvAnnotation.setText(annotation);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    private String getAnnotation(int position) {
        Trace t = data.get(position);
        Trace prev = position > 0 ? data.get(position-1) : null;

        if (t.isReaderToTag()) {
            byte[] data = t.getData();

            switch (data[0]) {
                case Iso14443.REQA:     return "REQA";
                case Iso14443.WUPA:     return "WUPA";
                case Iso14443.ANTICOLLISION:
                    if (data.length > 1 && data[1] == 0x70) return "SELECT";
                    return "ANTICOLL";
                case Iso14443.ANTICOLLISION_2:
                    if (data.length > 1 && data[1] == 0x70) return "SELECT_2";
                    return "ANTICOLL_2";
                case Iso14443.ANTICOLLISION_3:
                    if (data.length > 1 && data[1] == 0x70) return "SELECT_3";
                    return "ANTICOLL_3";
                case Iso14443.HLTA:     return "HLTA";
                case Iso14443.RATS:     return "RATS";
            }
        } else {
            byte[] lastReaderFrameData = prev.getData();
            switch (lastReaderFrameData[0]) {
                case Iso14443.ANTICOLLISION:
                    if (lastReaderFrameData.length > 1 && lastReaderFrameData[1] == 0x70) return "SAK";
                    return "UID";
                case Iso14443.ANTICOLLISION_2:
                    if (lastReaderFrameData.length > 1 && lastReaderFrameData[1] == 0x70) return "SAK";
                    return "UID_2";
                case Iso14443.ANTICOLLISION_3:
                    if (lastReaderFrameData.length > 1 && lastReaderFrameData[1] == 0x70) return "SAK";
                    return "UID_3";
                case Iso14443.REQA:
                case Iso14443.WUPA:
                    return "ATQA";
                case Iso14443.RATS:
                    return "ATS";
            }
        }
        switch (t.getData()[0] & 0xc0) {
            case 0x00: return "I-BLOCK";
            case 0x80: return "R-BLOCK";
            case 0xc0: return "S-BLOCK";
        }

        return "";
    }

}
