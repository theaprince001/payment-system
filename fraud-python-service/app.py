from fastapi import FastAPI
from pydantic import BaseModel, Field
import joblib
import numpy as np

model = joblib.load('fraud_model.joblib')

app = FastAPI(title="Fraud Detection Service")

class RiskRequest(BaseModel):
    payer_id: str = Field(alias="payerId")
    payee_id: str = Field(alias="payeeId")
    amount: float
    payment_method_type: str = Field(alias="paymentMethodType")
    payment_method_identifier: str = Field(alias="paymentMethodIdentifier")
    user_account_age_days: int = Field(alias="userAccountAgeDays")
    payment_method_age_seconds: int = Field(alias="paymentMethodAgeSeconds")
    transaction_velocity: int = Field(alias="transactionVelocity")

    model_config = {
        "populate_by_name": True
    }

class RiskResponse(BaseModel):
    score: float
    decision: str

@app.post("/predict", response_model=RiskResponse)
def predict_risk(request: RiskRequest):
    features = np.array([[
        request.amount,
        request.user_account_age_days,
        request.payment_method_age_seconds,
        request.transaction_velocity
    ]])
    score = model.predict_proba(features)[0][1]

    if score > 0.7:
        decision = "BLOCK"
    elif score > 0.3:
        decision = "REVIEW"
    else:
        decision = "ALLOW"

    return RiskResponse(score=score, decision=decision)