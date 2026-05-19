package com.pdfsplitter;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.apache.pdfbox.android.PDFBoxResourceLoader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.util.Matrix;

import java.io.File;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_PDF = 1001;
    private Uri selectedPdfUri;
    private boolean isHorizontalSplit = false;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        PDFBoxResourceLoader.init(getApplicationContext());

        Button btnSelect = findViewById(R.id.btnSelectPdf);
        Button btnSplit = findViewById(R.id.btnSplit);
        tvStatus = findViewById(R.id.tvStatus);
        RadioGroup rgSplit = findViewById(R.id.rgSplitType);

        btnSelect.setOnClickListener(v -> openFilePicker());
        rgSplit.setOnCheckedChangeListener((group, checkedId) -> isHorizontalSplit = checkedId == R.id.rbHorizontal);
        btnSplit.setOnClickListener(v -> {
            if (selectedPdfUri == null) {
                Toast.makeText(this, "请先选择PDF", Toast.LENGTH_SHORT).show();
                return;
            }
            new SplitTask().execute();
        });
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/pdf");
        startActivityForResult(intent, REQUEST_PDF);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQUEST_PDF && res == RESULT_OK && data != null) {
            selectedPdfUri = data.getData();
            tvStatus.setText("已选择：" + selectedPdfUri.getLastPathSegment());
        }
    }

    private class SplitTask extends AsyncTask<Void, String, Boolean> {
        @Override
        protected void onPreExecute() {
            tvStatus.setText("正在处理...");
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            try (InputStream is = getContentResolver().openInputStream(selectedPdfUri);
                 PDDocument srcDoc = PDDocument.load(is);
                 PDDocument newDoc = new PDDocument()) {

                for (PDPage page : srcDoc.getPages()) {
                    PDRectangle rect = page.getMediaBox();
                    float w = rect.getWidth();
                    float h = rect.getHeight();
                    if (isHorizontalSplit) {
                        splitHorizontal(page, newDoc, w, h);
                    } else {
                        splitVertical(page, newDoc, w, h);
                    }
                }

                File out = new File(getExternalFilesDir(null), "split_result.pdf");
                newDoc.save(out);
                publishProgress("完成，文件：" + out.getAbsolutePath());
                return true;
            } catch (Exception e) {
                publishProgress("失败：" + e.getMessage());
                return false;
            }
        }

        @Override
        protected void onProgressUpdate(String... values) {
            tvStatus.setText(values[0]);
        }

        @Override
        protected void onPostExecute(Boolean ok) {
            Toast.makeText(MainActivity.this, ok ? "分割成功" : "分割失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void splitVertical(PDPage src, PDDocument doc, float w, float h) throws Exception {
        PDPage p1 = new PDPage(new PDRectangle(0, 0, w / 2, h));
        copyArea(src, p1, doc, 0, 0, w / 2, h, 0, 0);
        doc.addPage(p1);

        PDPage p2 = new PDPage(new PDRectangle(0, 0, w / 2, h));
        copyArea(src, p2, doc, w / 2, 0, w / 2, h, -w / 2, 0);
        doc.addPage(p2);
    }

    private void splitHorizontal(PDPage src, PDDocument doc, float w, float h) throws Exception {
        PDPage p1 = new PDPage(new PDRectangle(0, 0, w, h / 2));
        copyArea(src, p1, doc, 0, h / 2, w, h / 2, 0, -h / 2);
        doc.addPage(p1);

        PDPage p2 = new PDPage(new PDRectangle(0, 0, w, h / 2));
        copyArea(src, p2, doc, 0, 0, w, h / 2, 0, 0);
        doc.addPage(p2);
    }

    private void copyArea(PDPage src, PDPage dst, PDDocument doc,
                          float sx, float sy, float w, float h, float tx, float ty) throws Exception {
        PDFormXObject form = new PDFormXObject(src);
        form.setBBox(new PDRectangle(sx, sy, w, h));
        try (PDPageContentStream cs = new PDPageContentStream(doc, dst)) {
            cs.transform(Matrix.getTranslateInstance(tx, ty));
            cs.drawForm(form);
        }
    }
}
