# Project TODO

- [x] Confirm that Discord-style profile work targets the Android application.
- [x] Implement a Discord-inspired Android user profile with identity, handle, bio, color and Premium state.
- [x] Add an Android avatar picker with a persistent public avatar image and graceful fallback initials.
- [x] Add display-name and @username editing with clear validation and availability feedback.
- [x] Add Android system-file opening through a FileProvider and the device's compatible installed apps.
- [x] Remove ordinary Premium restrictions so all users can use normal profile and chat features.
- [x] Keep the Ryazha author code separate from standard user functionality for explicit extra author features only.
- [x] Fix the duplicate sendTyping declaration that blocked the Android release build.
- [x] Test OTP login, profile endpoints, handle validation, chat, media and WebSocket reconnect behavior against the server.
- [x] Verify the Android release build and commit all completed changes under the repository owner's identity.
- [x] Reproduce and fix Android avatar upload/profile persistence, including its server-side validation and retrieval path.
- [x] Add a user-selectable profile banner with persistent media storage and readable automatic color fallbacks.
- [x] Add tappable chat identities that open public user profiles with an action to start a private conversation.
- [x] Extend the transport and storage model for private E2EE conversations routed by the server without plaintext access.
- [x] Add regression coverage for profile media and private conversation routing, then verify the Android release build.
- [x] Apply the SAssist Labs organization name consistently to Android-facing branding and server documentation.
- [x] Prepare provider-ready configuration and deployment documentation for a free no-card SAssist Labs backend.
- [x] Reconfigure the deployment path for a free no-card tier and document its non-persistent storage limitation.
