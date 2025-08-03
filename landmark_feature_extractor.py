#!/usr/bin/env python3
"""Utility to prepare landmark features for AR mapping.

- Loads `route.json` to gather landmark metadata.
- For each landmark image located in `app/src/main/assets/landmark_images`,
  compute ORB keypoints and descriptors.
- Saves descriptors to `app/src/main/assets/landmark_features/<landmark_id>.npz`
  for efficient loading on device.
- Provides helper functions for loading descriptors and matching a frame
  against the stored landmarks using a ratio test and RANSAC filtering.
"""

from __future__ import annotations

import json
import os
from dataclasses import dataclass
from typing import List, Tuple

try:
    import cv2
except ModuleNotFoundError as exc:  # pragma: no cover - handled at runtime
    raise SystemExit(
        "OpenCV (cv2) is required. Install it with 'pip install opencv-python-headless'."
    ) from exc

import numpy as np

ROUTE_FILE = "app/src/main/assets/route.json"
IMAGE_DIR = "app/src/main/assets/landmark_images"
FEATURE_DIR = "app/src/main/assets/landmark_features"


@dataclass
class Landmark:
    """Representation of a single landmark from the route file."""

    id: str
    name: str
    x: float
    y: float
    image_path: str


# ---------------------------------------------------------------------------
# JSON loading
# ---------------------------------------------------------------------------

def load_landmarks(route_file: str = ROUTE_FILE) -> List[Landmark]:
    """Load landmark definitions from the provided route JSON file."""
    with open(route_file, "r", encoding="utf-8") as fh:
        data = json.load(fh)

    landmarks: List[Landmark] = []
    for path_item in data.get("route", {}).get("path", []):
        for part in path_item.get("routeParts", []):
            for lm in part.get("landmarks", []):
                lm_id = lm.get("id")
                name = lm.get("nameDe") or lm.get("nameEn") or lm_id
                try:
                    x = float(lm.get("x", 0))
                    y = float(lm.get("y", 0))
                except (TypeError, ValueError):
                    x = 0.0
                    y = 0.0
                image_path = os.path.join(IMAGE_DIR, f"{lm_id}.jpg")
                landmarks.append(Landmark(lm_id, name, x, y, image_path))
    return landmarks


# ---------------------------------------------------------------------------
# Feature extraction and persistence
# ---------------------------------------------------------------------------

def compute_and_store_features(landmarks: List[Landmark], feature_dir: str = FEATURE_DIR) -> None:
    """Compute ORB features for all landmarks and save them to disk."""
    os.makedirs(feature_dir, exist_ok=True)
    orb = cv2.ORB_create(nfeatures=500)

    for lm in landmarks:
        if not os.path.exists(lm.image_path):
            print(f"[WARN] No image found for {lm.id} -> {lm.image_path}")
            continue

        img = cv2.imread(lm.image_path, cv2.IMREAD_GRAYSCALE)
        if img is None:
            print(f"[WARN] Could not read image {lm.image_path}")
            continue

        keypoints, descriptors = orb.detectAndCompute(img, None)
        if descriptors is None or len(keypoints) == 0:
            print(f"[WARN] No features found for {lm.id}")
            continue

        kp_array = np.array(
            [(kp.pt[0], kp.pt[1], kp.angle, kp.response, kp.octave, kp.class_id) for kp in keypoints],
            dtype=np.float32,
        )
        out_file = os.path.join(feature_dir, f"{lm.id}.npz")
        np.savez_compressed(out_file, keypoints=kp_array, descriptors=descriptors)
        print(f"[INFO] Saved {lm.id} with {len(keypoints)} keypoints -> {out_file}")


# ---------------------------------------------------------------------------
# Matching helper
# ---------------------------------------------------------------------------

def load_features(lm_id: str, feature_dir: str = FEATURE_DIR) -> Tuple[List[cv2.KeyPoint], np.ndarray]:
    data = np.load(os.path.join(feature_dir, f"{lm_id}.npz"))
    kp_data = data["keypoints"]
    keypoints = [
        cv2.KeyPoint(x=float(x), y=float(y), _size=1, _angle=a, _response=r, _octave=int(o), _class_id=int(c))
        for x, y, a, r, o, c in kp_data
    ]
    return keypoints, data["descriptors"]


def match_frame(frame: np.ndarray, landmarks: List[Landmark], feature_dir: str = FEATURE_DIR,
                ratio: float = 0.75) -> List[Tuple[Landmark, int]]:
    """Match a camera frame against stored landmark features.

    Returns a list of tuples ``(landmark, inlier_count)`` sorted by best match.
    """
    orb = cv2.ORB_create(nfeatures=500)
    frame_kp, frame_des = orb.detectAndCompute(frame, None)
    if frame_des is None:
        return []

    matcher = cv2.BFMatcher(cv2.NORM_HAMMING, crossCheck=False)
    results: List[Tuple[Landmark, int]] = []
    for lm in landmarks:
        feat_path = os.path.join(feature_dir, f"{lm.id}.npz")
        if not os.path.exists(feat_path):
            continue
        kp_lm, des_lm = load_features(lm.id, feature_dir)
        matches = matcher.knnMatch(des_lm, frame_des, k=2)
        good = [m for m, n in matches if m.distance < ratio * n.distance]
        if len(good) >= 4:
            src = np.float32([kp_lm[m.queryIdx].pt for m in good]).reshape(-1, 1, 2)
            dst = np.float32([frame_kp[m.trainIdx].pt for m in good]).reshape(-1, 1, 2)
            _, mask = cv2.findHomography(src, dst, cv2.RANSAC, 5.0)
            inliers = int(mask.sum()) if mask is not None else 0
            results.append((lm, inliers))
    return sorted(results, key=lambda t: t[1], reverse=True)


def main() -> None:
    landmarks = load_landmarks()
    compute_and_store_features(landmarks)


if __name__ == "__main__":
    main()
