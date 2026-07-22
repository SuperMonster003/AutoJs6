// OpenCV Java API/resources only. Native libraries are supplied by the OpenCV plugin.

plugins {
    id("org.autojs.build.local-arr-register-convention")
}

localAars {
    files += "opencv-java-4.8.0.aar"
}
