fn main() {
    println!("cargo:rustc-link-search=native=../golang_bench/lib");
    println!("cargo:rustc-link-lib=static=nextkv");
    println!("cargo:rustc-link-lib=c++");
}