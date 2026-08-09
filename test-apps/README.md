# Test applications

This directory contains installable Android applications used only for cross-process and hostile conformance testing. They are part of the repository test surface, not production plugins, host fallbacks, or release distributions.

Production plugin implementations live in independent sibling repositories. Test applications keep separate package identities and debug-only signing so malformed callbacks, invalid output, timeouts, and other adversarial behavior never need to be included in a production plugin.
