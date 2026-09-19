# CodePulse Lite — Live Coding Classroom

CodePulse Lite is a real-time coding classroom where teachers can conduct live coding sessions, launch programming challenges, and review student submissions with AI-assisted feedback.

The project is built with **Spring Boot, React, WebSockets/STOMP, MySQL, Spring Security/JWT, JPA/Hibernate, and Google Gemini**.

## Features

- 🔐 JWT-based authentication with Teacher and Student roles
- 🏫 Teacher classroom creation with unique room codes
- 👨‍💻 Real-time teacher code synchronization
- ⚡ WebSocket/STOMP-based live communication
- 📝 Teacher-created coding challenges
- ⏱️ Server-enforced challenge countdown
- 💻 Independent student solution editor
- 🤖 Gemini-powered AI code review
- 📊 AI score and constructive feedback
- 📡 Real-time teacher submission dashboard
- 🔎 Secure submission/code viewing
- 🔒 Role-based authorization
- 🗄️ MySQL persistence using JPA/Hibernate

## Tech Stack

### Backend

- Java 21
- Spring Boot 3.3.3
- Spring Security
- JWT
- Spring Data JPA
- Hibernate
- MySQL
- WebSocket
- STOMP
- Maven

### Frontend

- React
- Vite
- React Router
- Axios
- Monaco Editor
- STOMP.js

### AI

- Google Gemini API
- Gemini 3.6 Flash
- Structured JSON responses for code evaluation

## Architecture

```text
                    CODEPULSE LITE
                          │
          ┌───────────────┼───────────────┐
          │               │               │
          ▼               ▼               ▼
   Spring Security    WebSocket/STOMP   Gemini AI
          │               │               │
        JWT          Live Code Sync    Code Review
          │          Challenges        Score + Feedback
          │          Submissions
          ▼               │               │
       Spring Boot ◄──────┴───────────────┘
          │
          ▼
     JPA / Hibernate
          │
          ▼
        MySQL


🔄 How It Works
1. Teacher registers and logs in.
2. Teacher creates a classroom and receives a unique room code.
3. Student joins the classroom using the room code.
4. Teacher writes code in the live coding editor.
5. Students see the teacher's code in real time.
6. Teacher creates and starts a coding challenge.
7. Students solve the challenge independently.
8. Students submit their solutions before the timer expires.
9. Gemini AI reviews the submitted code.
10. The score and feedback are saved in the database.
11. Teacher receives the submission status in real time.
12. Teacher can securely view the submitted code and AI feedback.



🤖 Gemini AI Code Review
CodePulse Lite uses Gemini 3.6 Flash to provide AI-assisted code evaluation.
The AI review returns:
- Score
- Code quality feedback
- Suggestions for improvement

🔐 Security
The application uses Spring Security and JWT authentication.
Role-based authorization is implemented for teacher and student operations.
Sensitive configuration such as:
- Database password
- JWT secret
- Gemini API key
is loaded through environment variables and is not stored in the repository.

⚙️ Environment Variables
Backend
DB_URL=jdbc:mysql://localhost:3306/codepulse_db
DB_USERNAME=root
DB_PASSWORD=your_database_password

JWT_SECRET=your_jwt_secret
JWT_EXPIRATION_MS=86400000

GEMINI_API_KEY=your_gemini_api_key
GEMINI_MODEL=gemini-3.6-flash

WS_ALLOWED_ORIGINS=http://localhost:5173


CodePulse/
├── client/
│   ├── src/
│   │   ├── components/
│   │   ├── context/
│   │   ├── hooks/
│   │   ├── pages/
│   │   ├── services/
│   │   └── utils/
│   ├── package.json
│   └── .env.example
│
├── src/
│   ├── main/
│   │   ├── java/com/codepulse/
│   │   │   ├── config/
│   │   │   ├── controller/
│   │   │   ├── dto/
│   │   │   ├── entity/
│   │   │   ├── repository/
│   │   │   └── service/
│   │   └── resources/
│   │       └── application.properties
│   │
│   └── test/
│
├── pom.xml
└── README.md

🔮 Future Improvements
- Multi-language coding support
- Secure code execution sandbox
- Teacher analytics and performance reports
- Persistent classroom history
- Production deployment
- Additional AI-assisted learning features

Made with ❤️ by Anmol Kumar Srivastava