import pandas as pd
import numpy as np
from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score, classification_report
import joblib

# NOTE: This synthetic data uses deterministic rules to label fraud.
# The model is learning to recover those rules; accuracy metrics here
# reflect rule recovery, not real-world fraud detection performance.
# For a real system, use historical fraud data with noise.
# Generate synthetic data (replace with real data if available)
np.random.seed(42)
n_samples = 5000

amount = np.random.uniform(100, 100000, n_samples)
user_age = np.random.randint(0, 365, n_samples)
method_age = np.random.randint(0, 3600, n_samples)
velocity = np.random.randint(0, 10, n_samples)

fraud = ((amount > 50000) & (user_age < 30) & (velocity > 5)) | \
        (amount > 90000) | \
        ((method_age < 10) & (velocity > 7))

X = pd.DataFrame({
    'amount': amount,
    'user_account_age_days': user_age,
    'payment_method_age_seconds': method_age,
    'transaction_velocity': velocity
})
y = fraud.astype(int)

X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

model = RandomForestClassifier(n_estimators=100, random_state=42)
model.fit(X_train, y_train)

y_pred = model.predict(X_test)
print("Accuracy:", accuracy_score(y_test, y_pred))
print(classification_report(y_test, y_pred))

joblib.dump(model, 'fraud_model.joblib')
print("Model saved to fraud_model.joblib")