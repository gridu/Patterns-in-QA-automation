import os
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, RedirectResponse
from fastapi.staticfiles import StaticFiles

from app.database import Base, engine
from app.routers import auth, products, cart


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Initialize database tables on startup
    try:
        Base.metadata.create_all(bind=engine)
    except Exception as exc:
        print(f"[Warning] Failed to initialize database tables: {exc}")
    yield


app = FastAPI(
    title="Shop SUT API",
    description="System Under Test for Design Patterns in Test Automation Course",
    version="1.0.0",
    lifespan=lifespan
)

# Allow all origins for convenient API testing from any framework / host port
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include API Routers
app.include_router(products.router)
app.include_router(cart.router)
app.include_router(auth.router)

# Mount static frontend directory if present
static_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), "static")
if os.path.exists(static_dir):
    app.mount("/static", StaticFiles(directory=static_dir), name="static")


@app.get("/", include_in_schema=False)
def index():
    index_file = os.path.join(static_dir, "index.html")
    if os.path.exists(index_file):
        return FileResponse(index_file)
    return RedirectResponse(url="/docs")
