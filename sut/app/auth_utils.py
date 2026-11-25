import uuid
from typing import Optional, Tuple
from fastapi import Request, Response, Depends, HTTPException, status
from sqlalchemy.orm import Session
from app.database import get_db
from app.models import User

SESSION_COOKIE_NAME = "session_id"
USER_COOKIE_NAME = "user_id"


def get_session_and_user(
    request: Request,
    response: Response,
    db: Session = Depends(get_db)
) -> Tuple[str, Optional[User]]:
    """
    Extracts the current session_id and authenticated User (if any).
    Supports both Cookies and HTTP Headers (X-Session-ID, X-User-ID) for test flexibility.
    """
    # 1. Resolve Session ID
    session_id = request.headers.get("X-Session-ID") or request.cookies.get(SESSION_COOKIE_NAME)
    if not session_id:
        session_id = f"guest_{uuid.uuid4().hex[:16]}"
        response.set_cookie(
            key=SESSION_COOKIE_NAME,
            value=session_id,
            httponly=False,
            samesite="lax"
        )

    # 2. Resolve Authenticated User
    user = None
    user_id_val = request.headers.get("X-User-ID") or request.cookies.get(USER_COOKIE_NAME)
    if user_id_val:
        try:
            user_id = int(user_id_val)
            user = db.query(User).filter(User.id == user_id).first()
        except ValueError:
            user = None

    return session_id, user


def get_active_cart_key(session_id: str, user: Optional[User]) -> str:
    """
    Returns the session_id key used in cart_items table:
    If user is authenticated, their cart is stored under 'user_{user.id}'.
    Otherwise, under their guest session_id.
    """
    if user:
        return f"user_{user.id}"
    return session_id


def require_auth(
    session_and_user: Tuple[str, Optional[User]] = Depends(get_session_and_user)
) -> User:
    _, user = session_and_user
    if not user:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentication required"
        )
    return user
