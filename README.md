# 🧠 Local Science Tutor (On-Device RAG for Android)

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![MediaPipe](https://img.shields.io/badge/MediaPipe-00B4D8?style=for-the-badge&logo=google&logoColor=white)
![ObjectBox](https://img.shields.io/badge/ObjectBox-002147?style=for-the-badge&logo=database&logoColor=white)

An advanced, **100% offline** Android application that implements a complete Retrieval-Augmented Generation (RAG) pipeline directly on your phone. Upload any PDF textbook, and the AI will read, index, and answer questions about it using a local Gemma 3 model—without ever sending your data to the cloud!

---

## ✨ Key Features
* **Completely Offline:** Zero API calls to OpenAI or cloud providers. Complete privacy and local execution.
* **On-Device Vector Database:** Uses ObjectBox's blazing-fast HNSW Vector Search to find relevant textbook paragraphs in milliseconds.
* **Dynamic PDF Ingestion:** Rips text from local PDFs and mathematically chunks them into the vector database on the fly.
* **Local LLM Inference:** Runs Google's 4-bit quantized Gemma 3 (1B) model directly on the Android CPU via MediaPipe.
* **Intelligent Auto-Downloader:** Automatically fetches the required heavy AI models (~1.3 GB) from public HuggingFace CDN mirrors on the first launch.
* **Beautiful Material 3 UI:** Features full Markdown rendering, interactive chat bubbles, and an edge-to-edge full-screen reading mode.

---

## 🏗️ How It Works (The RAG Pipeline)

When you upload a PDF and ask a question, the app performs the following steps entirely on your phone's processor:

1. **Extraction:** `PDFBox-Android` extracts raw text from the document and splits it into safe 1,000-character chunks.
2. **Embedding:** The `all-MiniLM-L6-v2` ONNX model mathematically converts each text chunk into a 384-dimensional vector array.
3. **Storage:** These vectors are saved into the `ObjectBox` NoSQL database using an `@HnswIndex` optimized for Cosine Similarity.
4. **Retrieval:** When you ask a question, your question is embedded into a vector. ObjectBox performs a Nearest Neighbor (KNN) search to instantly find the 3 most relevant textbook chunks.
5. **Generation:** The context chunks and your question are combined into a strict prompt and fed to the `Gemma 3` LLM via MediaPipe.
6. **Rendering:** The LLM's response is streamed to the UI and parsed into beautiful typography using `multiplatform-markdown-renderer-m3`.

---

## 📚 Tech Stack & Resources

### Artificial Intelligence
* **[Gemma 3 (1B IT Int4)](https://huggingface.co/AfiOne/gemma3-1b-it-int4.task):** The core conversational LLM, compiled to a `.task` file for MediaPipe.
* **[all-MiniLM-L6-v2 (ONNX)](https://huggingface.co/Xenova/all-MiniLM-L6-v2):** The lightweight embedding model used to map semantic meaning.
* **[MediaPipe Tasks GenAI](https://developers.google.com/mediapipe/solutions/genai/llm_inference):** Google's official Android SDK for running quantized LLMs on-device.
* **[Sentence-Embeddings-Android](https://github.com/shubham0204/Sentence-Embeddings-Android):** An ONNX Runtime wrapper by Shubham0204 for executing NLP embedding models on Android.

### Database & Utilities
* **[ObjectBox Kotlin](https://objectbox.io/):** An ultra-fast, local NoSQL database with built-in Vector Search capabilities.
* **[PDFBox-Android](https://github.com/TomRoush/PdfBox-Android):** A port of Apache PDFBox for parsing PDF documents on Android.
* **[Multiplatform Markdown Renderer](https://github.com/mikepenz/multiplatform-markdown-renderer):** Mike Penz's robust Markdown parser for Jetpack Compose Material 3.

---

## 🚀 Installation & Setup

1. **Clone the repository:**
   ```bash
   git clone https://github.com/yourusername/Local_Rag.git
   cd Local_Rag
   ```

2. **Open in Android Studio:**
   Ensure you are using **Android Studio Ladybug** (or any version utilizing Android Gradle Plugin 9.0+ with Built-in Kotlin Support).

3. **Build & Run:**
   * Build the project and deploy it to a physical Android device (Recommended: 8GB+ RAM). 
   * *Note: Emulators may struggle with the memory requirements of local LLMs.*

4. **First Launch (Auto-Download):**
   Upon opening the app, you will be prompted to download the required AI models. Ensure you are on a stable Wi-Fi connection. The app will automatically stream and securely store ~1.3 GB of `.onnx` and `.task` files into its isolated `context.filesDir`.

---

## 📱 Usage

1. **Start the App:** Wait for the models to initialize.
2. **Upload a Textbook:** Tap the 📎 (paperclip) icon in the top right corner and select a PDF file from your phone.
3. **Wait for Indexing:** The app will freeze briefly while it rips the PDF, embeds the text, and writes the vectors to ObjectBox.
4. **Chat:** Ask a question! 
   * *Example:* "Explain the process of photosynthesis."
   * The AI will answer and explicitly cite the source document.
5. **Full Screen Mode:** Tap on any AI response bubble to open the answer in a full-screen, scrollable Markdown reader!

---

## ⚠️ Known Limitations
* **RAM Constraints:** The 4-bit Gemma model requires approximately 1 GB of continuous free memory. Devices with aggressive RAM management may kill the app during generation.
* **PDF Formatting:** Complex PDFs with multi-column layouts or heavy image content may produce messy text chunks, slightly degrading the RAG context quality.

---
*Built with ❤️ utilizing purely local, on-device computing.*