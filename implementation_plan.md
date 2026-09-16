# DevPilot Deployment Plan

Your app is in great shape for production! We have a working Next.js frontend, a containerized Spring Boot backend, and a PostgreSQL database. 

Since you are looking for free-tier platforms, we will use a modernized, robust stack that won't cost you a dime.

## Architecture & Platforms

We will use the following free platforms:
1. **Database:** [Neon.tech](https://neon.tech/) (An excellent serverless PostgreSQL platform with a generous free tier that fully supports the `pgvector` extension we need).
2. **Backend:** [Render.com](https://render.com/) (Allows deploying Docker containers for free, perfect for our Spring Boot `Dockerfile`).
3. **Frontend:** [Vercel](https://vercel.com/) (The creators of Next.js; offers the best free hosting for Next.js applications).

> [!NOTE]
> Render's free tier spins down the backend after 15 minutes of inactivity. When a request hits it after it's asleep, it may take ~50 seconds to wake up (cold start). For a free personal project, this is completely normal!

## User Review Required

Since I cannot create accounts on these third-party platforms for you, I will need you to set them up. Below is the step-by-step plan on how we will execute this deployment together. Let me know if you approve of this platform stack, and we will begin!

## Step 1: Database Setup (Neon)
1. Go to [Neon.tech](https://neon.tech/) and create a free project.
2. Under your project settings, copy the **PostgreSQL Connection String**.
3. *Provide the connection string to me* so I can verify the connection and `pgvector` compatibility from my end.

## Step 2: Backend Deployment (Render)
1. You will create a Web Service on Render linked to your GitHub repository.
2. We will configure Render to use the existing `backend/Dockerfile`.
3. We will supply the necessary environment variables (`DATABASE_URL` from Neon, `ENCRYPTION_KEY`, `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`).
4. Once deployed, Render will give us a live backend URL (e.g., `https://devpilot-backend.onrender.com`).

## Step 3: Frontend Deployment (Vercel)
1. You will import your GitHub repository into Vercel.
2. We will set the root directory to `client/`.
3. We will add the environment variables, specifically pointing `NEXT_PUBLIC_API_URL` to the new Render backend URL.
4. You will update your GitHub OAuth App's callback URL to the new Vercel domain.

## Open Questions

- Do you already have an account on Neon, Render, or Vercel? 
- Have you pushed your latest local code to a GitHub repository? (Render and Vercel will need to pull from your GitHub repo to deploy).
