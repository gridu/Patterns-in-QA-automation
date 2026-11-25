from typing import List, Literal, Optional
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session
from sqlalchemy import asc, desc
from app.database import get_db
from app.models import Product
from app.schemas import ProductResponse

router = APIRouter(prefix="/api/products", tags=["Products"])


@router.get("", response_model=List[ProductResponse])
def list_products(
    sort_by: Optional[Literal["name", "price"]] = Query("name", description="Field to sort by"),
    order: Optional[Literal["asc", "desc"]] = Query("asc", description="Sort order"),
    db: Session = Depends(get_db)
):
    """
    Returns the catalog of products.
    Products with quantity == 0 are strictly excluded.
    """
    query = db.query(Product).filter(Product.quantity > 0)

    # Determine sorting column
    sort_column = Product.name if sort_by == "name" else Product.price
    order_func = desc if order == "desc" else asc

    return query.order_by(order_func(sort_column)).all()


@router.get("/{product_id}", response_model=ProductResponse)
def get_product(product_id: int, db: Session = Depends(get_db)):
    """
    Returns single product details by ID.
    """
    product = db.query(Product).filter(Product.id == product_id).first()
    if not product:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Product not found"
        )
    return product
