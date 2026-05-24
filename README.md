# CoDrive

**CoDrive** is an advanced, voice-controlled Android application designed as a general-purpose agentic assistant. Built on an Accessibility Service architecture, CoDrive empowers users to navigate and interact with their device entirely hands-free across various environments. By seamlessly bridging spoken commands with complex on-screen actions, it serves as a conversational co-pilot for your mobile experience.

## 🚀 Key Features

* **Hands-Free Navigation:** Utilizes Android's Accessibility Service to autonomously parse UI trees and execute clicks, scrolls, and typing based on natural language intent.
* **Agentic UI Interaction:** Employs advanced orchestrators and LLM decision-parsing to translate high-level user requests into actionable UI nodes.
* **Continuous Voice Processing:** Features robust local Speech-to-Text (STT) and Text-to-Speech (TTS) capabilities using Sherpa-ONNX for fast, private, and continuous audio processing.
* **Multimodal AI Integration:** Supports flexible LLM backends (including Gemini and Groq) alongside (eventually) Vision-Language Model (VLM) runtimes like InternVL for deep screen-context understanding.
* **Persistent Overlay:** Runs via a floating system overlay bubble, ensuring the assistant is always contextually aware and instantly accessible without interrupting the user's current task.
* **Contextual Memory:** Maintains session context and identity databases to ensure interactions feel natural, continuous, and tailored over time.

## 🛠️ Technical Stack

* **Language:** Kotlin & Java
* **Framework:** Android SDK (Accessibility API, System Overlays)
* **AI/ML:** Gemini API, Groq API, Sherpa-ONNX (Local STT/TTS), InternVL
* **Architecture:** Clean architecture emphasizing modularity across execution engines, orchestration loops, and memory management.
* **Local Storage:** Room/SQLite (Identity & Session Daos)

---

## 🧪 How to Beta Test

We welcome beta testers to help refine CoDrive's agentic navigation and voice recognition models. Follow the steps below to build, install, and test the application on your Android device.

### Prerequisites
* An Android device running Android 11.0 (API Level 30) or higher.
* Android Studio (latest version recommended) for building from source.
* *Optional:* API keys for Gemini or Groq to enable full LLM capabilities (configurable within the app settings).

### Installation (Build from Source)
1.  **Clone the Repository:**
    ```bash
    git clone https://github.com/your-username/codrive.git
    cd codrive
    ```
2.  **Open in Android Studio:** Open the project folder in Android Studio and allow Gradle to sync dependencies.
3.  **Build and Deploy:** Connect your Android device via USB debugging or Wireless ADB and click **Run** (`Shift + F10`).

### Configuration & Permissions setup
Because CoDrive interacts directly with the system UI, it requires specific permissions to function correctly:
1.  **Microphone Access:** Required for continuous speech recognition.
2.  **Display Over Other Apps:** Go to `Settings > Apps > Special app access > Display over other apps` and enable it for CoDrive. This allows the floating agent bubble to appear.
3.  **Accessibility Service:** * Navigate to `Settings > Accessibility > Installed apps`.
    * Select **CoDrive Accessibility Service** and toggle it **On**. 
    * *Note: CoDrive strictly uses this permission to read screen context and perform actions on your behalf locally. Please review the privacy policy for details.*

### Running a Test Session
1.  Open the **CoDrive app** and navigate to **Settings** to input your preferred LLM provider details (e.g., Groq or Gemini).
2.  Tap **Start Service** to initialize the overlay bubble and background orchestrator.
3.  Wake the assistant by tapping the overlay bubble.
4.  Give a natural language command (e.g., *"Open my settings and turn on Bluetooth"* or *"Scroll down and read the first paragraph"*).

## 🤝 Contributing

Contributions are welcome! If you'd like to improve the UI parsing logic, add support for additional local models, or refine the speech endpointer, please fork the repository and submit a pull request.
