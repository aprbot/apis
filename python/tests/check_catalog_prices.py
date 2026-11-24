"""Read public model prices using an ordinary signed-in cabinet account."""

from decimal import Decimal
import os

import requests


def test_canary_and_dolphin_are_available_at_the_published_prices():
    response = requests.get(
        os.environ["AIMOS_HOST"].rstrip("/") + "/api/v2/cabinet/catalog",
        headers={"Authorization": "Bearer " + os.environ["AIMOS_CABINET_TOKEN"]},
        timeout=30,
    )
    response.raise_for_status()
    catalog = response.json()
    assert catalog["currency"] == "RUB"
    models = {model["code"]: model for model in catalog["results"]}
    expected = {"dolphin": (250, 250, 250), "canary-1b-v2": (Decimal("1666.67"), 0, 0)}
    for code, rates in expected.items():
        model = models[code]
        assert model["status"] == "listed" and model["can_create_key"]
        price = model["price"]
        assert price["currency"] == "RUB"
        assert tuple(Decimal(price[field]) for field in (
            "input_per_million", "cached_input_per_million", "output_per_million",
        )) == rates
