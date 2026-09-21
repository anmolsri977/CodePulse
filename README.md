# ⚡ CodePulse Lite — Live Coding Classroom

CodePulse Lite is a real-time collaborative coding classroom built with **Spring Boot, React, WebSockets/STOMP, MySQL, Spring Security, JWT, and Gemini AI**.

It allows teachers to conduct live coding sessions, broadcast code changes to students in real time, create timed coding challenges, and review student submissions using AI-assisted code analysis.

---

## ✨ Features

### 👨‍🏫 Teacher

- Secure teacher authentication using JWT
- Create and manage coding classrooms
- Generate unique 6-character room codes
- Live code in a Monaco-based editor
- Broadcast code changes to connected students in real time
- Create coding challenges
- Set challenge time limits
- Start challenges for all students
- Monitor student submissions live
- View submitted student code
- Receive AI-generated code reviews

### 👨‍🎓 Student

- Secure student authentication
- Join classrooms using a room code
- View teacher's live code in read-only mode
- Receive coding challenges in real time
- Solve challenges independently
- Submit solutions before the timer expires
- Receive Gemini AI feedback and score
- View submission results

### 🤖 Gemini AI Code Review

Student submissions are analyzed using Google's Gemini API.

The AI provides:

- Code quality feedback
- Correctness observations
- Potential improvements
- Score from 0–100
- Concise suggestions for better implementation

If the AI service is temporarily unavailable, the submission is still saved safely.

---

## 🛠️ Tech Stack

### Backend

- Java 21
- Spring Boot 3.3.3
- Spring Security
- JWT Authentication
- Spring Data JPA
- Hibernate
- MySQL
- WebSocket
- STOMP
- Maven
- Google Gemini API

### Frontend

- React
- Vite
- JavaScript
- React Router
- Axios
- Monaco Editor
- STOMP.js
- CSS

---

## 🏗️ Architecture

```text
                    CODEPULSE LITE
                          │
          ┌───────────────┼───────────────┐
          ↓               ↓               ↓
   Spring Security    WebSocket         Gemini
          │               │               │
         JWT          Live Code       Code Review
          │           Challenge            │
          ↓           Status               ↓
        Users            │              Feedback
                          │
                          ↓
                    Spring Boot
                          │
                          ↓
                   JPA / Hibernate
                          │
                          ↓
                        MySQL
```

---

## 🔄 How It Works

### 1. Authentication

Users register as either:

- `TEACHER`
- `STUDENT`

After login, the backend issues a JWT token.

Protected REST APIs require authentication using the JWT.

---

### 2. Teacher Creates a Classroom

The teacher creates a classroom from the dashboard.

CodePulse generates a unique 6-character room code.

Students use this code to join the classroom.

---

### 3. Live Coding

The teacher writes code inside the Monaco Editor.

Code changes are sent through:

```text
STOMP
   ↓
/app/room/{roomCode}/editor
   ↓
Spring Boot WebSocket
   ↓
/topic/room/{roomCode}/editor
   ↓
Connected Students
```

Students receive the teacher's code in real time through their read-only editor.

---

### 4. Coding Challenge

The teacher creates a challenge containing:

- Challenge title
- Description
- Starter code
- Time limit

The teacher starts the challenge.

The challenge is broadcast to all connected students through WebSocket/STOMP.

---

### 5. Challenge Timer

The challenge start time is stored on the backend.

The backend calculates the actual deadline instead of trusting only the frontend timer.

Students cannot submit after the server-enforced deadline.

The frontend also displays a live countdown:

```text
10:00
09:59
09:58
...
00:01
00:00
```

---

### 6. Student Submission

Students submit their solution through:

```text
POST /api/challenges/{challengeId}/submit
```

The backend:

1. Authenticates the student
2. Verifies the challenge
3. Checks room status
4. Checks the submission deadline
5. Sends the code to Gemini
6. Saves the submission
7. Stores the AI score and feedback
8. Broadcasts submission status to the teacher

---

### 7. AI Code Review

The backend sends the submitted code to Gemini.

Gemini returns structured review information such as:

```json
{
  "score": 95,
  "feedback": "The solution is correct and concise. Consider improving formatting and handling potential integer overflow."
}
```

The result is stored with the submission.

---

### 8. Live Submission Monitoring

Teachers subscribe to:

```text
/topic/room/{roomCode}/submissions
```

Whenever a student submits, the teacher receives an update containing:

- Student name
- Challenge
- Score
- Submission time
- Submission status

The teacher can then open the submitted code for inspection.

---

## 🔐 Security

CodePulse Lite uses several Spring Security features.

### JWT Authentication

REST APIs are protected using stateless JWT authentication.

### Password Hashing

Passwords are stored using BCrypt hashing.

### Role-Based Authorization

Different operations are restricted based on user roles.

For example:

```text
TEACHER
 ├── Create room
 ├── Close room
 ├── Create challenge
 ├── Start challenge
 ├── Broadcast live code
 └── View student submissions

STUDENT
 ├── Join room
 ├── Receive live code
 └── Submit challenge
```

### WebSocket Security

WebSocket connections require JWT authentication during the STOMP `CONNECT` phase.

Students cannot publish teacher-only live code.

Clients cannot directly publish to server-controlled submission topics.

---

## 🌐 REST API Overview

### Authentication

```text
POST /api/auth/register
POST /api/auth/login
```

### Rooms

```text
POST  /api/rooms
GET   /api/rooms/my
POST  /api/rooms/join/{roomCode}
PATCH /api/rooms/{roomCode}/close
```

### Challenges

```text
POST /api/rooms/{roomCode}/challenges
GET  /api/rooms/{roomCode}/challenges
```

### Submissions

```text
POST /api/challenges/{challengeId}/submit
GET  /api/submissions/{submissionId}
```

---

## 🔌 WebSocket Endpoints

WebSocket endpoint:

```text
/ws
```

STOMP destinations:

### Live Editor

```text
/app/room/{roomCode}/editor
/topic/room/{roomCode}/editor
```

### Challenges

```text
/app/room/{roomCode}/challenge
/topic/room/{roomCode}/challenge
```

### Submission Updates

```text
/topic/room/{roomCode}/submissions
```

---

## ⚙️ Environment Variables

Create the required environment variables before running the backend.

```text
DB_URL=jdbc:mysql://localhost:3306/codepulse_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC

DB_USERNAME=root

DB_PASSWORD=your_mysql_password

JWT_SECRET=your_jwt_secret

GEMINI_API_KEY=your_gemini_api_key

GEMINI_MODEL=gemini-3.6-flash

GEMINI_BASE_URL=https://generativelanguage.googleapis.com/v1beta

CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000

WS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000,http://localhost:8080
```

The application uses environment variables so database credentials, JWT secrets, and Gemini API keys are not hardcoded into the source code.

---

## 🚀 Running Locally

### Prerequisites

Make sure you have:

- Java 21+
- Maven
- MySQL
- Node.js
- npm
- Gemini API key

---

### 1. Clone the Repository

```bash
git clone https://github.com/anmolsri977/CodePulse.git

cd CodePulse
```

---

### 2. Configure MySQL

Create the database:

```sql
CREATE DATABASE codepulse_db;
```

Configure your environment variables:

```text
DB_USERNAME=root
DB_PASSWORD=your_password
```

Hibernate will create/update the required tables automatically.

---

### 3. Start the Backend

From the project root:

```bash
mvn spring-boot:run
```

Backend runs on:

```text
http://localhost:8080
```

---

### 4. Start the Frontend

Open another terminal:

```bash
cd client
npm install
npm run dev
```

Frontend runs on:

```text
http://localhost:5173
```

---

## 🧪 Testing

The backend includes automated tests covering authentication, rooms, challenges, submissions, WebSocket behavior, and Gemini integration.

Run:

```bash
mvn test
```

Frontend production build:

```bash
cd client
npm run build
```

---

## 📁 Project Structure

```text
CodePulse/
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── .../
│   │   │
│   │   └── resources/
│   │       └── application.properties
│   │
│   └── test/
│       └── java/
│
├── client/
│   ├── src/
│   │   ├── components/
│   │   ├── pages/
│   │   ├── hooks/
│   │   ├── services/
│   │   └── utils/
│   │
│   ├── package.json
│   └── vite.config.js
│
├── screenshots/
│   ├── live-classroom.png
│   ├── challenges-submissions.png
│   ├── student-classroom.png
│   └── Submission-timeout.png
│
├── .gitignore
├── pom.xml
└── README.md
```

---

## 📸 Screenshots

### Live Coding Classroom

Teachers can broadcast code changes to connected students in real time using WebSocket/STOMP.

![CodePulse Live Coding Classroom](screenshots/live-classroom.png)

---

### Challenges & Live Submissions

Teachers can create timed coding challenges and monitor student submissions in real time.

![CodePulse Challenges and Submissions](screenshots/challenges-submissions.png)

---

### Student Coding Workspace

Students receive the live teacher code in read-only mode, solve the active challenge in their own editor, and submit their solution before the timer expires.

![CodePulse Student Classroom](screenshots/student-classroom.png)

---

## 🧠 Key Engineering Concepts Demonstrated

This project demonstrates practical implementation of:

- Spring Boot REST APIs
- Spring Security
- JWT authentication
- BCrypt password hashing
- Role-based authorization
- JPA / Hibernate
- MySQL persistence
- WebSocket communication
- STOMP messaging
- Real-time state synchronization
- React state management
- Monaco Editor integration
- Axios API communication
- Server-side deadline enforcement
- AI API integration
- Structured AI responses
- Automated backend testing
- Environment-based configuration

---

## 🔮 Future Improvements

Possible future improvements include:

- Multiple programming language support
- Code execution sandbox
- Automated test-case evaluation
- Teacher analytics dashboard
- Student performance history
- Classroom attendance tracking
- Challenge leaderboard
- Redis-based scaling for WebSocket sessions
- Docker deployment
- Cloud deployment
- More advanced AI code analysis

---

## 👨‍💻 Author

**Anmol Kumar Srivastava**

B.Tech — Computer Science & Engineering

GitHub: [@anmolsri977](https://github.com/anmolsri977)

---

## ⭐ Project

If you find the project interesting, consider giving the repository a star!

**CodePulse Lite — Live Coding Classroom**