#pragma once

namespace gb {
struct VideoRect {
    int x;
    int y;
    int width;
    int height;
};

// Preserve the renderer's fitted dimensions; place unused vertical space below the picture.
inline VideoRect topAlignedVideoRect(int width, int height, int horizontalMargin, int verticalMargin) {
    return {horizontalMargin, 0, width - horizontalMargin * 2, height - verticalMargin * 2};
}
}
