# 🏋️‍♂️ MoveInsight: AI-Powered Biomechanical Analysis

[![Android](https://img.shields.io/badge/Android-3DDC84?style=flat-square&logo=android&logoColor=white)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-0095D5?style=flat-square&logo=kotlin&logoColor=white)](#)
[![Python](https://img.shields.io/badge/Python-3776AB?style=flat-square&logo=python&logoColor=white)](#)
[![FastAPI](https://img.shields.io/badge/FastAPI-009688?style=flat-square&logo=fastapi&logoColor=white)](#)
[![TensorFlow](https://img.shields.io/badge/TensorFlow-FF6F00?style=flat-square&logo=tensorflow&logoColor=white)](#)
[![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white)](#)

> **MoveInsight** is an intelligent system for the biomechanical analysis of strength training and injury prevention using computer vision. 
> *Bachelor's Thesis - Computer Engineering (University of Salamanca).*

## 📖 Project Overview

Accurate biomechanical analysis has traditionally been limited to clinical environments equipped with expensive sensors. **MoveInsight** democratizes this access by allowing any user to analyze their squat technique using only a standard smartphone camera.

The system extracts the user's 3D pose, evaluates movement quality through Machine Learning and Deep Learning algorithms, and provides a physical tracking system (fatigue, pain, and workload) to **proactively prevent injuries**.

## ✨ Main Features

*   📹 **Sensor-free 3D Analysis:** 3D pose estimation from 2D video using *MediaPipe BlazePose*.
*   🤖 **2-Stage AI Engine:**
    *   **BiLSTM Network:** Analyzes the temporal sequence to segment the exercise into phases (descent, bottom, ascent) and accurately count repetitions.
    *   **XGBoost:** Evaluates 5 key biomechanical KPIs per repetition (Depth, Torso inclination, Knee valgus, Symmetry, and Rhythm).
*   🛡️ **Injury Prevention (Readiness):** An algorithm that calculates readiness to train by combining:
    *   Acute:Chronic Workload Ratio (ACWR).
    *   Rate of Perceived Exertion (Borg RPE Scale).
    *   Post-workout pain (VAS Scale).
    *   Technical degradation.
*   📱 **Native Android App:** Fluid and intuitive interface developed in Kotlin using Jetpack Compose.
*   📊 **Reports & Feedback:** Video generation with skeleton overlay and detailed PDF report export.

## 🏗️ System Architecture

The project follows a distributed and scalable architecture divided into two main components:

### 1. Android Application (Frontend)
Developed following **Clean Architecture** principles and the **MVVM** pattern.
*   **UI:** Jetpack Compose, Material 3.
*   **Network & Asynchrony:** Retrofit, OkHttp, Coroutines.
*   **Dependency Injection:** Dagger Hilt.
*   **Background Tasks:** WorkManager for tracking notifications and reminders.

### 2. API & Biomechanical Engine (Backend)
An asynchronous backend designed to process heavy video tasks without blocking the client.
*   **Core:** FastAPI (Python), Uvicorn.
*   **AI & CV:** OpenCV, TensorFlow/Keras (BiLSTM), XGBoost, Scikit-Learn.
*   **Database:** MariaDB with SQLAlchemy (ORM).
*   **Infrastructure:** Deployed via Docker and Docker Compose, using Nginx as a secure reverse proxy (HTTPS/TLS).

## 📂 Repository Structure

```text
moveinsight/
├── backend/                # FastAPI server and Artificial Intelligence engine
│   ├── app/                # Endpoints (routers), services, and DB models
│   ├── entrenamiento/      # Training scripts for BiLSTM and XGBoost + Datasets
│   ├── processing/         # Computer Vision Pipeline (MediaPipe, preprocessing)
│   └── Dockerfile          # Backend container configuration
├── frontend/               # Native Android app source code
│   └── app/src/main/java/com/moveinsight/
│       ├── core/           # Network, security, and utilities configuration
│       ├── domain/         # Business models and Use Cases (Clean Architecture)
│       └── presentation/   # Jetpack Compose UI and ViewModels (MVVM)
├── nginx/                  # Reverse proxy configuration and SSL certificates
└── docker-compose.yml      # Service orchestration (API, DB, Nginx)

```

## 🚀 Deployment and Installation

The server environment is fully dockerized to facilitate deployment on any machine.

1. **Clone the repository:**
```bash
git clone [https://github.com/your-username/moveinsight.git](https://github.com/your-username/moveinsight.git)
cd moveinsight

```


2. **Configure environment variables:**
Create an `.env` file in the `backend/` directory based on the necessary database configuration and JWT keys.
3. **Deploy with Docker:**
```bash
docker-compose up -d --build

```


This will spin up the database (MariaDB), API (FastAPI), and proxy (Nginx) containers. *For more details, check the `DEPLOY.md` file.*
4. **Build the Android App:**
Open the `frontend/` folder with Android Studio, sync the project with Gradle, configure the server IP in the environment, and build the APK.

## 🎓 Authorship and Academic Context

* **Author:** Diego Gómez Terradillos
* **Advisor:** Dña. Carolina Zato Domínguez
* **Institution:** University of Salamanca (Faculty of Science)
* **Documentation:** You can check the complete thesis (in Spanish) in the attached [`Memoria.pdf`](https://www.google.com/search?q=./Memoria.pdf) file in this repository for a comprehensive breakdown of the mathematical models and architecture.

---

*If you found this project interesting or useful, feel free to drop a ⭐ on the repository!*

```<img width="988" height="2048" alt="WhatsApp Image 2026-08-10 at 21 01 55" src="https://github.com/user-attachments/assets/d8bc020a-b0a6-4e0b-922b-22173928a281" />
