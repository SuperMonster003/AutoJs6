/**

This is an example how to write your own simple pngquant using libimagequant.

libimagequant works with any PNG library. This example uses lodepng, because it's easier to use than libpng.

To run this example, run these commands in the library directory:

    make example
    ./example /path/to/some_file.png

On "manually":

1. Get lodepng.c (download lodepng.cpp and rename it) and lodepng.h
   from http://lodev.org/lodepng/ and put them in this directry

2. Compile libimagequant (see README.md)

3. Compile and run the example:

   gcc -O3 example.c libimagequant.a -o example
   ./example truecolor_file.png


This example code can be freely copied under CC0 (public domain) license.
*/

#include "lodepng.h" // Get it from https://raw.githubusercontent.com/lvandeve/lodepng/master/lodepng.h
#include "lodepng.c" // Get it from https://raw.githubusercontent.com/lvandeve/lodepng/master/lodepng.cpp and rename
#include <stdio.h>
#include <stdlib.h>
#include "libimagequant.h"

int main(int argc, char *argv[]) {
    if (argc < 2) {
        fprintf(stderr, "Please specify a path to a PNG file\n");
        return EXIT_FAILURE;
    }

    const char *input_png_file_path = argv[1];

    // Load PNG file and decode it as raw RGBA pixels
    // This uses lodepng library for PNG reading (not part of libimagequant)

    unsigned int width, height;
    unsigned char *raw_rgba_pixels;
    unsigned int status = lodepng_decode32_file(&raw_rgba_pixels, &width, &height, input_png_file_path);
    if (status) {
        fprintf(stderr, "Can't load %s: %s\n", input_png_file_path, lodepng_error_text(status));
        return EXIT_FAILURE;
    }

    // Use libimagequant to make a palette for the RGBA pixels

    liq_attr *handle = liq_attr_create();
    liq_image *input_image = liq_image_create_rgba(handle, raw_rgba_pixels, width, height, 0);
    // You could set more options here, like liq_set_quality
    liq_result *quantization_result;
    if (liq_image_quantize(input_image, handle, &quantization_result) != LIQ_OK) {
        fprintf(stderr, "Quantization failed\n");
        return EXIT_FAILURE;
    }

    // Use libimagequant to make new image pixels from the palette

    size_t pixels_size = width * height;
    unsigned char *raw_8bit_pixels = malloc(pixels_size);
    liq_set_dithering_level(quantization_result, 1.0);

    liq_write_remapped_image(quantization_result, input_image, raw_8bit_pixels, pixels_size);
    const liq_palette *palette = liq_get_palette(quantization_result);

    // Save converted pixels as a PNG file
    // This uses lodepng library for PNG writing (not part of libimagequant)

    LodePNGState state;
    lodepng_state_init(&state);
    state.info_raw.colortype = LCT_PALETTE;
    state.info_raw.bitdepth = 8;
    state.info_png.color.colortype = LCT_PALETTE;
    state.info_png.color.bitdepth = 8;

    for(int i=0; i < palette->count; i++) {
        lodepng_palette_add(&state.info_png.color, palette->entries[i].r, palette->entries[i].g, palette->entries[i].b, palette->entries[i].a);
        lodepng_palette_add(&state.info_raw, palette->entries[i].r, palette->entries[i].g, palette->entries[i].b, palette->entries[i].a);
    }

    unsigned char *output_file_data;
    size_t output_file_size;
    unsigned int out_status = lodepng_encode(&output_file_data, &output_file_size, raw_8bit_pixels, width, height, &state);
    if (out_status) {
        fprintf(stderr, "Can't encode image: %s\n", lodepng_error_text(out_status));
        return EXIT_FAILURE;
    }

    const char *output_png_file_path = "quantized_example.png";
    FILE *fp = fopen(output_png_file_path, "wb");
    if (!fp) {
        fprintf(stderr, "Unable to write to %s\n", output_png_file_path);
        return EXIT_FAILURE;
    }
    fwrite(output_file_data, 1, output_file_size, fp);
    fclose(fp);

    printf("Written %s\n", output_png_file_path);

    // Done. Free memory.

    liq_result_destroy(quantization_result); // Must be freed only after you're done using the palette
    liq_image_destroy(input_image);
    liq_attr_destroy(handle);

    free(raw_8bit_pixels);
    lodepng_state_cleanup(&state);
}
